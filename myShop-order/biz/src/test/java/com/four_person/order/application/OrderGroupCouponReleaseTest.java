package com.four_person.order.application;

import com.four_person.common.api.Result;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderCoupon;
import com.four_person.order.domain.model.OrderGroup;
import com.four_person.order.domain.repository.OrderGroupRepository;
import com.four_person.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 券释放黄金不变量测试（OrderGroupService 真实逻辑）：
 * 释放券必须用组的核销单号（第一张子单）——用其他子单号释放会被折扣服务静默忽略；
 * 只有整组全部关闭（CANCELLED/REFUNDED）才释放并关组；释放按核销明细去重，一张券只退一次。
 */
@ExtendWith(MockitoExtension.class)
class OrderGroupCouponReleaseTest {

    private static final String GROUP_NO = "MG-TEST-0001";
    private static final String ORDER_A = "MS-TEST-000A";
    private static final String ORDER_B = "MS-TEST-000B";

    @Mock private OrderGroupRepository orderGroupRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private DiscountInternalApi discountInternalApi;

    private OrderGroupService service() {
        return new OrderGroupService(orderGroupRepository, orderRepository, discountInternalApi);
    }

    private OrderGroup group(String status) {
        OrderGroup group = new OrderGroup();
        group.setGroupNo(GROUP_NO);
        group.setUserId(900201L);
        group.setStatus(status);
        group.setCouponUseOrderNo(ORDER_A);
        return group;
    }

    private Order child(String orderNo, String status, Long couponId) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setGroupNo(GROUP_NO);
        order.setCouponId(couponId);
        return order;
    }

    private OrderCoupon redeemed(long couponId) {
        OrderCoupon coupon = new OrderCoupon();
        coupon.setOrderNo(ORDER_A);
        coupon.setCouponId(couponId);
        coupon.setDiscount(BigDecimal.ONE);
        return coupon;
    }

    private void stubCouponApi() {
        when(discountInternalApi.releaseCoupon(any(Long.class), any(CouponReleaseRequest.class)))
                .thenReturn(Result.success(null));
    }

    @Test
    void releasesEveryDistinctCouponWithCanonicalOrderNoWhenGroupFullyClosed() {
        OrderGroup paid = group(OrderStatus.PAID.name());
        Order refundedA = child(ORDER_A, OrderStatus.REFUNDED.name(), 10L);
        Order refundedB = child(ORDER_B, OrderStatus.REFUNDED.name(), 10L);
        when(orderGroupRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(paid));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(refundedA, refundedB));
        // A 单用了券 10、11，B 单核销明细里重复记录了券 10 → distinct 后只释放 10、11
        when(orderRepository.findCoupons(ORDER_A)).thenReturn(List.of(redeemed(10L), redeemed(11L)));
        when(orderRepository.findCoupons(ORDER_B)).thenReturn(List.of(redeemed(10L)));
        stubCouponApi();
        when(orderGroupRepository.markClosed(eq(GROUP_NO), any())).thenReturn(true);

        service().releaseCouponsIfGroupFullyClosed(refundedA);

        // 释放单号必须是组的核销单号（第一张子单），而非触发释放的那张子单
        ArgumentCaptor<CouponReleaseRequest> captor = ArgumentCaptor.forClass(CouponReleaseRequest.class);
        verify(discountInternalApi, times(2)).releaseCoupon(any(Long.class), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(request ->
                assertThat(request.getOrderNo()).isEqualTo(ORDER_A));
        verify(discountInternalApi).releaseCoupon(eq(10L), any(CouponReleaseRequest.class));
        verify(discountInternalApi).releaseCoupon(eq(11L), any(CouponReleaseRequest.class));
        verify(orderGroupRepository).markClosed(eq(GROUP_NO), any());
    }

    @Test
    void keepsCouponWhileAnyChildStillActive() {
        OrderGroup paid = group(OrderStatus.PAID.name());
        Order refundedA = child(ORDER_A, OrderStatus.REFUNDED.name(), 10L);
        Order activeB = child(ORDER_B, OrderStatus.PAID.name(), 10L);
        when(orderGroupRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(paid));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(refundedA, activeB));

        // 兄弟子单仍享受优惠 → 不释放、不关组
        service().releaseCouponsIfGroupFullyClosed(refundedA);

        verify(discountInternalApi, never()).releaseCoupon(any(), any());
        verify(orderGroupRepository, never()).markClosed(anyString(), any());
    }

    @Test
    void unpaidGroupNeverTriggersCloseRelease() {
        // 只有已支付组才有「退款关组」路径；未支付组的取消走整组取消，不经此方法
        OrderGroup unpaid = group(OrderStatus.UNPAID.name());
        Order cancelledA = child(ORDER_A, OrderStatus.CANCELLED.name(), 10L);
        when(orderGroupRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(unpaid));

        service().releaseCouponsIfGroupFullyClosed(cancelledA);

        verify(orderRepository, never()).findByGroupNo(anyString());
        verify(discountInternalApi, never()).releaseCoupon(any(), any());
        verify(orderGroupRepository, never()).markClosed(anyString(), any());
    }

    @Test
    void fallsBackToChildPrimaryCouponWhenRedemptionRowsMissing() {
        OrderGroup paid = group(OrderStatus.PAID.name());
        Order refundedA = child(ORDER_A, OrderStatus.CANCELLED.name(), 10L);
        Order refundedB = child(ORDER_B, OrderStatus.REFUNDED.name(), 11L);
        when(orderGroupRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(paid));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(refundedA, refundedB));
        when(orderRepository.findCoupons(ORDER_A)).thenReturn(List.of());
        when(orderRepository.findCoupons(ORDER_B)).thenReturn(List.of());
        stubCouponApi();
        when(orderGroupRepository.markClosed(eq(GROUP_NO), any())).thenReturn(true);

        service().releaseCouponsIfGroupFullyClosed(refundedA);

        // 历史数据兜底：核销明细缺失时退回子单主券 10、11
        verify(discountInternalApi).releaseCoupon(eq(10L), any(CouponReleaseRequest.class));
        verify(discountInternalApi).releaseCoupon(eq(11L), any(CouponReleaseRequest.class));
    }

    @Test
    void ignoresOrdersWithoutGroup() {
        Order single = child("MS-SINGLE-1", OrderStatus.CANCELLED.name(), 10L);
        single.setGroupNo(null);

        service().releaseCouponsIfGroupFullyClosed(single);

        verify(orderGroupRepository, never()).findByGroupNo(anyString());
        verify(discountInternalApi, never()).releaseCoupon(any(), any());
    }

    @Test
    void directReleaseUsesGivenGroupAndChildren() {
        OrderGroup paid = group(OrderStatus.PAID.name());
        Order cancelledA = child(ORDER_A, OrderStatus.CANCELLED.name(), 10L);
        when(orderRepository.findCoupons(ORDER_A)).thenReturn(List.of(redeemed(10L)));
        stubCouponApi();

        service().releaseCoupons(paid, List.of(cancelledA));

        ArgumentCaptor<CouponReleaseRequest> captor = ArgumentCaptor.forClass(CouponReleaseRequest.class);
        verify(discountInternalApi).releaseCoupon(eq(10L), captor.capture());
        assertThat(captor.getValue().getOrderNo()).isEqualTo(ORDER_A);
    }
}
