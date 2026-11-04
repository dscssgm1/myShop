package com.four_person.order.application;

import com.four_person.common.api.Results;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderCoupon;
import com.four_person.order.domain.model.OrderGroup;
import com.four_person.order.domain.repository.OrderGroupRepository;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 跨店合单订单组（父订单）共享逻辑。
 *
 * <p>券释放黄金不变量：只要组内还有一个子订单在享受券优惠，券就不能释放；
 * 只有整组关闭（全部子订单 CANCELLED / REFUNDED）时，券才按核销单号全额退回。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGroupService {

    /** 子订单的关闭终态：全部子订单进入终态才视为整组关闭 */
    private static final List<String> CLOSED_CHILD_STATUSES = List.of(
            OrderStatus.CANCELLED.name(), OrderStatus.REFUNDED.name());

    private final OrderGroupRepository orderGroupRepository;
    private final OrderRepository orderRepository;
    private final DiscountInternalApi discountInternalApi;

    public OrderGroup requireGroup(String groupNo) {
        return orderGroupRepository.findByGroupNo(groupNo)
                .orElseThrow(() -> new com.four_person.common.exception.BusinessException(
                        OrderErrorCode.ORDER_GROUP_NOT_FOUND));
    }

    /**
     * 子订单走到关闭终态（取消/退款）后调用：若整组已全部关闭，则整组退回优惠券并关组。
     * 幂等：券释放按核销单号在折扣服务侧幂等；关组走条件更新，并发下只成功一次。
     */
    public void releaseCouponsIfGroupFullyClosed(Order child) {
        if (!StringUtils.hasText(child.getGroupNo())) {
            return;
        }
        orderGroupRepository.findByGroupNo(child.getGroupNo())
                .filter(OrderGroup::isPaid)
                .ifPresent(group -> {
                    List<Order> children = orderRepository.findByGroupNo(group.getGroupNo());
                    boolean allClosed = children.stream()
                            .allMatch(o -> CLOSED_CHILD_STATUSES.contains(o.getStatus()));
                    if (!allClosed) {
                        return;
                    }
                    releaseCoupons(group, children);
                    if (!orderGroupRepository.markClosed(group.getGroupNo(), LocalDateTime.now())) {
                        log.info("订单组 {} 已被并发关闭，跳过重复关组", group.getGroupNo());
                    }
                });
    }

    /**
     * 整组退回优惠券：按组的核销单号（第一张子单）释放——user_coupon.order_no 记录的是该单号，
     * 用其他子单号释放会被折扣服务静默忽略。子单核销明细里同一张券会重复出现，distinct 后只释放一次。
     */
    public void releaseCoupons(OrderGroup group, List<Order> children) {
        LinkedHashSet<Long> couponIds = new LinkedHashSet<>();
        for (Order child : children) {
            for (OrderCoupon coupon : orderRepository.findCoupons(child.getOrderNo())) {
                couponIds.add(coupon.getCouponId());
            }
            // 历史数据兜底：核销明细缺失时退回子单上的主券
            if (child.getCouponId() != null) {
                couponIds.add(child.getCouponId());
            }
        }
        String useOrderNo = StringUtils.hasText(group.getCouponUseOrderNo())
                ? group.getCouponUseOrderNo()
                : children.get(0).getOrderNo();
        for (Long couponId : couponIds) {
            CouponReleaseRequest request = new CouponReleaseRequest();
            request.setOrderNo(useOrderNo);
            Results.data(discountInternalApi.releaseCoupon(couponId, request));
        }
    }
}
