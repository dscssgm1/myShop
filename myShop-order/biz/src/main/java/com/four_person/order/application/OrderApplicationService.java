package com.four_person.order.application;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountItem;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.logistics.api.LogisticsInternalApi;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.merchant.api.MerchantInternalApi;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.order.api.dto.CreateOrderVO;
import com.four_person.order.api.dto.OrderCreateRequest;
import com.four_person.order.api.dto.OrderGroupInternalDTO;
import com.four_person.order.api.dto.OrderGroupVO;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.OrderItemRequest;
import com.four_person.order.api.dto.OrderItemVO;
import com.four_person.order.api.dto.OrderPreviewItemVO;
import com.four_person.order.api.dto.OrderPreviewRequest;
import com.four_person.order.api.dto.OrderPreviewVO;
import com.four_person.order.api.dto.OrderVO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.dto.UserStatsVO;
import com.four_person.order.api.dto.OrderInternalDtos;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderCoupon;
import com.four_person.order.domain.model.OrderGroup;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.model.UserStats;
import com.four_person.order.domain.repository.OrderGroupRepository;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.order.infrastructure.support.OrderNoGenerator;
import com.four_person.order.infrastructure.support.PointsRuleReader;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.payment.api.PaymentInternalApi;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockChangeRequest;
import com.four_person.system.api.MemberInternalApi;
import com.four_person.system.api.dto.MemberDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 订单应用服务：下单时按店铺拆分，每个店铺生成一个独立订单（一店一单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal FREIGHT_AMOUNT = ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** 物流签收后买家未手动确认，自动完成订单的天数 */
    private static final int AUTO_COMPLETE_DAYS = 7;

    private final OrderRepository orderRepository;
    private final ProductInternalApi productInternalApi;
    private final DiscountInternalApi discountInternalApi;
    private final OrderNoGenerator orderNoGenerator;
    private final MessageInternalApi messageInternalApi;
    private final PaymentInternalApi paymentInternalApi;
    private final MerchantInternalApi merchantInternalApi;
    private final LogisticsInternalApi logisticsInternalApi;
    private final MemberInternalApi memberInternalApi;
    private final CommissionApplicationService commissionApplicationService;
    private final OrderGroupRepository orderGroupRepository;
    private final OrderGroupService orderGroupService;
    private final PointsRuleReader pointsRuleReader;

    // ==================== 预览 ====================

    public OrderPreviewVO preview(Long userId, OrderPreviewRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponIds(),
                request.getPromotionIds(), Boolean.TRUE.equals(request.getUsePoints()));
        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setItems(prepared.allItems.stream().map(this::toPreviewItemVO).toList());
        vo.setShops(buildShopPreviews(prepared));
        vo.setOriginalAmount(prepared.originalAmount);
        vo.setPromotionDiscount(prepared.discount.getPromotionDiscount());
        vo.setCouponDiscount(prepared.discount.getCouponDiscount());
        vo.setTotalDiscount(prepared.discount.getTotalDiscount());
        vo.setFreightAmount(FREIGHT_AMOUNT);
        vo.setPayAmount(prepared.discount.getPayAmount());
        vo.setMerchantBearAmount(prepared.discount.getMerchantBearAmount());
        vo.setPlatformBearAmount(prepared.discount.getPlatformBearAmount());
        vo.setAppliedPromotionId(prepared.discount.getAppliedPromotionId());
        vo.setAppliedPromotionTitle(prepared.discount.getAppliedPromotionTitle());
        vo.setAppliedCouponId(prepared.discount.getAppliedCouponId());
        vo.setAppliedCouponTitle(prepared.discount.getAppliedCouponTitle());
        vo.setAppliedCoupons(toCouponUsages(prepared.discount));
        vo.setAppliedPromotionIds(prepared.discount.getAppliedPromotionIds());
        vo.setPromotionOptions(toPromotionOptions(prepared.discount.getPromotionOptions()));
        vo.setPointsAvailable(prepared.pointsAvailable);
        vo.setPointsUsed(prepared.pointsUsed);
        vo.setPointsDeduction(prepared.pointsDeduction);
        vo.setPointsPerYuan(prepared.pointsPerYuan);
        return vo;
    }

    // ==================== 创建订单（一店一单） ====================

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO create(Long userId, OrderCreateRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponIds(),
                request.getPromotionIds(), Boolean.TRUE.equals(request.getUsePoints()));
        List<AllocatedShop> allocations = allocateShops(prepared);
        List<DiscountResultVO.CouponUsage> couponUsages = prepared.discount.getAppliedCoupons() == null
                ? List.of() : prepared.discount.getAppliedCoupons();

        // 跨店结算生成订单组（父订单）：优惠记在组上按子单金额比例分摊，支付/取消以组为单位。
        // 券核销订单号取第一张子单（canonical），释放券时按组上的 couponUseOrderNo 幂等退回。
        boolean crossShop = allocations.size() > 1;
        String groupNo = crossShop ? orderNoGenerator.nextGroup() : null;

        List<CreateOrderVO.OrderBrief> briefs = new ArrayList<>();
        for (AllocatedShop allocated : allocations) {
            String orderNo = orderNoGenerator.next();
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setShopId(allocated.shopId);
            order.setShopName(allocated.shopName);
            order.setGroupNo(groupNo);
            order.setStatus(OrderStatus.UNPAID.name());
            order.setBizType("NORMAL");
            order.setOriginalAmount(allocated.originalAmount);
            order.setPromotionDiscount(allocated.promotionDiscount);
            order.setCouponDiscount(allocated.couponDiscount);
            order.setMerchantBearAmount(allocated.merchantBearAmount);
            order.setPlatformBearAmount(allocated.platformBearAmount);
            order.setPointsUsed(allocated.pointsUsed);
            order.setPointsDeduction(allocated.pointsDeduction);
            order.setFreightAmount(FREIGHT_AMOUNT);
            order.setPayAmount(allocated.payAmount);
            order.setPromotionId(prepared.discount.getAppliedPromotionId());
            order.setPromotionTitle(prepared.discount.getAppliedPromotionTitle());
            DiscountResultVO.CouponUsage primary = couponUsages.stream()
                    .filter(usage -> usage.getCouponId() != null
                            && usage.getCouponId().equals(prepared.discount.getAppliedCouponId()))
                    .findFirst().orElse(null);
            order.setCouponId(primary == null
                    ? prepared.discount.getAppliedCouponId() : primary.getCouponId());
            order.setCouponTitle(couponUsages.stream()
                    .map(DiscountResultVO.CouponUsage::getTitle)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("、")));
            order.setReceiverName(request.getReceiverName());
            order.setReceiverPhone(request.getReceiverPhone());
            order.setReceiverAddress(request.getReceiverAddress());
            order.setRemark(request.getRemark());
            orderRepository.save(order);

            List<OrderItem> items = allocated.items.stream()
                    .map(item -> toOrderItem(order, item))
                    .toList();
            orderRepository.saveItems(items);

            // 一单多券核销明细（取消订单时逐一释放）
            if (!couponUsages.isEmpty()) {
                orderRepository.saveCoupons(orderNo, couponUsages.stream().map(usage -> {
                    OrderCoupon orderCoupon = new OrderCoupon();
                    orderCoupon.setCouponId(usage.getCouponId());
                    orderCoupon.setTitle(usage.getTitle());
                    orderCoupon.setDiscount(usage.getDiscount() == null ? ZERO : usage.getDiscount());
                    return orderCoupon;
                }).toList());
            }

            briefs.add(new CreateOrderVO.OrderBrief(orderNo, allocated.shopId,
                    allocated.shopName, allocated.payAmount));
        }

        // 落库订单组（父订单）：金额取分摊前总额，与子单分摊之和严格相等
        if (crossShop) {
            OrderGroup group = new OrderGroup();
            group.setGroupNo(groupNo);
            group.setUserId(userId);
            group.setStatus(OrderStatus.UNPAID.name());
            group.setShopCount(allocations.size());
            group.setOriginalAmount(prepared.originalAmount);
            group.setPromotionDiscount(nz(prepared.discount.getPromotionDiscount()));
            group.setCouponDiscount(nz(prepared.discount.getCouponDiscount()));
            group.setTotalDiscount(nz(prepared.discount.getTotalDiscount()));
            group.setMerchantBearAmount(nz(prepared.discount.getMerchantBearAmount()));
            group.setPlatformBearAmount(nz(prepared.discount.getPlatformBearAmount()));
            group.setPointsUsed(prepared.pointsUsed);
            group.setPointsDeduction(prepared.pointsDeduction);
            group.setFreightAmount(FREIGHT_AMOUNT);
            group.setPayAmount(nz(prepared.discount.getPayAmount()));
            group.setPromotionId(prepared.discount.getAppliedPromotionId());
            group.setPromotionTitle(prepared.discount.getAppliedPromotionTitle());
            group.setCouponId(prepared.discount.getAppliedCouponId());
            group.setCouponTitle(couponUsages.stream()
                    .map(DiscountResultVO.CouponUsage::getTitle)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("、")));
            group.setReceiverName(request.getReceiverName());
            group.setReceiverPhone(request.getReceiverPhone());
            group.setReceiverAddress(request.getReceiverAddress());
            group.setRemark(request.getRemark());
            group.setCouponUseOrderNo(briefs.get(0).getOrderNo());
            orderGroupRepository.save(group);
        }

        // 扣减库存（按商品聚合）
        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setItems(prepared.stockItems);
        Results.data(productInternalApi.deductStock(stockRequest));

        // 逐张核销优惠券（核销单号取第一张子订单 = 组的 couponUseOrderNo）+ 扣抵现积分，
        // 任一失败：释放已核销的券、回滚库存并抛异常（本地落库随事务回滚）
        if (!couponUsages.isEmpty() || prepared.pointsUsed > 0) {
            String orderNo = briefs.get(0).getOrderNo();
            List<Long> usedCoupons = new ArrayList<>();
            try {
                for (DiscountResultVO.CouponUsage usage : couponUsages) {
                    CouponUseRequest useRequest = new CouponUseRequest();
                    useRequest.setUserId(userId);
                    useRequest.setOrderNo(orderNo);
                    Results.data(discountInternalApi.useCoupon(usage.getCouponId(), useRequest));
                    usedCoupons.add(usage.getCouponId());
                }
                if (prepared.pointsUsed > 0) {
                    // 按子单逐单扣积分：单号即幂等键，取消/退款按子单单号退回，两边天然对齐
                    for (int i = 0; i < allocations.size(); i++) {
                        AllocatedShop allocated = allocations.get(i);
                        if (allocated.pointsUsed() == null || allocated.pointsUsed() <= 0) {
                            continue;
                        }
                        MemberDtos.SpendPointsRequest spendRequest = new MemberDtos.SpendPointsRequest();
                        spendRequest.setUserId(userId);
                        spendRequest.setPoints(allocated.pointsUsed());
                        spendRequest.setBizNo(briefs.get(i).getOrderNo());
                        spendRequest.setRemark("下单抵现扣减积分");
                        Results.data(memberInternalApi.spend(spendRequest));
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("核销优惠券/扣抵现积分失败，回滚库存与已核销的券", ex);
                usedCoupons.forEach(couponId ->
                        releaseQuietly(couponId, orderNo));
                // 扣积分调用可能已落地（超时等），按子单号幂等退回；未落地则返回 0
                for (CreateOrderVO.OrderBrief brief : briefs) {
                    refundPointsQuietly(userId, brief.getOrderNo(), "下单失败退回抵现积分");
                }
                try {
                    Results.data(productInternalApi.restoreStock(stockRequest));
                } catch (Exception restoreEx) {
                    log.error("回滚库存失败，需要人工处理", restoreEx);
                }
                throw ex;
            }
        }

        briefs.forEach(brief -> notify(userId, "下单成功",
                "您在「" + brief.getShopName() + "」下单成功，订单号 " + brief.getOrderNo()
                        + "，应付 ¥" + brief.getPayAmount()
                        + (prepared.pointsUsed > 0
                        ? "（已用 " + prepared.pointsUsed + " 积分抵扣 ¥" + prepared.pointsDeduction + "）"
                        : "")));

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrders(briefs);
        vo.setOrderNo(briefs.get(0).getOrderNo());
        vo.setGroupNo(groupNo);
        vo.setPayAmount(briefs.stream().map(CreateOrderVO.OrderBrief::getPayAmount)
                .reduce(ZERO, BigDecimal::add));
        return vo;
    }

    private BigDecimal nz(BigDecimal amount) {
        return amount == null ? ZERO : amount;
    }

    /** 释放单张券，失败只记日志（失败路径尽力而为） */
    private void releaseQuietly(Long couponId, String orderNo) {
        try {
            CouponReleaseRequest releaseRequest = new CouponReleaseRequest();
            releaseRequest.setOrderNo(orderNo);
            Results.data(discountInternalApi.releaseCoupon(couponId, releaseRequest));
        } catch (Exception ex) {
            log.error("释放优惠券失败: couponId={}, orderNo={}", couponId, orderNo, ex);
        }
    }

    // ==================== 查询 ====================

    public PageResult<OrderVO> page(Long userId, String status, int page, int size) {
        return assemblePage(orderRepository.pageByUser(userId, status, page, size));
    }

    public PageResult<OrderVO> adminPage(String status, int page, int size) {
        return assemblePage(orderRepository.adminPage(status, page, size));
    }

    /** 商家端：本店铺的订单 */
    public PageResult<OrderVO> merchantPage(Long shopId, String status, int page, int size) {
        return assemblePage(orderRepository.pageByShop(shopId, status, page, size));
    }

    public OrderVO detail(Long userId, String orderNo) {
        return toVO(requireOwned(userId, orderNo));
    }

    // ==================== 状态流转 ====================

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        // 补款订单是退货申诉成立后的应付款项，取消会绕过补款限制
        if (order.isRecovery()) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "补款订单不可取消，请完成支付");
        }
        if (StringUtils.hasText(order.getGroupNo())) {
            cancelGroupChild(userId, order);
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            // 商家未发货：买家可取消，先退款再释放库存/优惠券
            if (!Boolean.TRUE.equals(refundOrder(order, "用户取消订单退款").getSuccess())) {
                throw new BusinessException(OrderErrorCode.REFUND_FAILED);
            }
            order.refundBeforeShip(now);
            if (!orderRepository.markRefundedAfterCancel(orderNo, now)) {
                throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
            }
            releaseAfterCancel(order);
            refundOrderPoints(order);
            notify(userId, "订单已取消并退款",
                    "订单 " + orderNo + " 已取消，退款将原路退回，库存与优惠券已释放");
            return;
        }

        if (!OrderStatus.UNPAID.name().equals(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "商家已发货，请通过售后处理");
        }

        order.cancel(now);
        if (!orderRepository.markCancelled(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        releaseAfterCancel(order);
        refundOrderPoints(order);
        notify(userId, "订单已取消", "订单 " + orderNo + " 已取消，库存与优惠券已释放");
    }

    /**
     * 整组取消未发货订单（跨店合单以组为单位）：
     * 组内全部待付款 → 整组取消并全额退回优惠券；
     * 部分已付款 → 只取消未付款子单，优惠券不退回（防「已付子单继续享优惠 + 券回笼再下单」被薅两次）；
     * 有已发货子单 → 拒绝，走售后。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelGroup(Long userId, String groupNo) {
        OrderGroup group = orderGroupService.requireGroup(groupNo);
        if (!group.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        List<Order> children = orderRepository.findByGroupNo(groupNo);
        if (children.isEmpty()) {
            throw new BusinessException(OrderErrorCode.ORDER_GROUP_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();

        boolean anyShipped = children.stream().anyMatch(o ->
                OrderStatus.SHIPPED.name().equals(o.getStatus())
                        || OrderStatus.COMPLETED.name().equals(o.getStatus())
                        || OrderStatus.AFTER_SALE.name().equals(o.getStatus()));
        if (anyShipped) {
            throw new BusinessException(OrderErrorCode.ORDER_GROUP_STATUS_INVALID,
                    "订单组内有订单已发货，已发货订单请通过售后处理");
        }

        if (children.stream().allMatch(o -> OrderStatus.UNPAID.name().equals(o.getStatus()))) {
            doCancelGroup(userId, group, children, now);
            return;
        }

        // 部分子单已支付：取消未付款子单，券不退回
        int cancelled = 0;
        for (Order child : children) {
            if (!OrderStatus.UNPAID.name().equals(child.getStatus())) {
                continue;
            }
            child.cancel(now);
            if (!orderRepository.markCancelled(child.getOrderNo(), now)) {
                throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID,
                        "订单 " + child.getOrderNo() + " 状态已变化，请刷新后重试");
            }
            restoreStockOf(child);
            refundOrderPoints(child);
            cancelled++;
        }
        if (cancelled == 0) {
            throw new BusinessException(OrderErrorCode.ORDER_GROUP_STATUS_INVALID,
                    "订单组内没有可取消的待付款订单");
        }
        notify(userId, "订单组部分取消",
                "合单 " + groupNo + " 已取消 " + cancelled + " 个未支付订单；"
                        + "优惠券随组内已支付订单保留，暂不退回");
    }

    /** 合单子订单逐单取消入口（用户在订单列表点某一子单的取消） */
    private void cancelGroupChild(Long userId, Order order) {
        LocalDateTime now = LocalDateTime.now();
        OrderGroup group = orderGroupService.requireGroup(order.getGroupNo());
        List<Order> children = orderRepository.findByGroupNo(group.getGroupNo());

        if (!OrderStatus.UNPAID.name().equals(order.getStatus())
                && !OrderStatus.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "商家已发货，请通过售后处理");
        }

        if (OrderStatus.UNPAID.name().equals(order.getStatus())) {
            boolean allUnpaid = children.stream()
                    .allMatch(o -> OrderStatus.UNPAID.name().equals(o.getStatus()));
            if (allUnpaid) {
                // 组内全部未支付：未支付取消以组为单位，券全额退回（淘宝购物车语义）
                doCancelGroup(userId, group, children, now);
            } else {
                // 部分子单已支付：只取消本单，库存归还但券不退回
                order.cancel(now);
                if (!orderRepository.markCancelled(order.getOrderNo(), now)) {
                    throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
                }
                restoreStockOf(order);
                notify(userId, "订单已取消",
                        "订单 " + order.getOrderNo() + " 已取消；合单中其他订单仍在使用优惠券，优惠券暂不退回");
            }
            return;
        }

        // PAID 子单：商家未发货，按分摊后的实付金额退款；券要等整组关闭才退回
        if (!Boolean.TRUE.equals(refundOrder(order, "用户取消订单退款").getSuccess())) {
            throw new BusinessException(OrderErrorCode.REFUND_FAILED);
        }
        order.refundBeforeShip(now);
        if (!orderRepository.markRefundedAfterCancel(order.getOrderNo(), now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        orderGroupService.releaseCouponsIfGroupFullyClosed(order);
        refundOrderPoints(order);
        notify(userId, "订单已取消并退款",
                "订单 " + order.getOrderNo() + " 已取消，退款 ¥" + order.getPayAmount() + " 将原路退回");
    }

    /** 整组取消（组内全部待付款）：逐单取消并归还库存，优惠券按组核销单号一次性退回 */
    private void doCancelGroup(Long userId, OrderGroup group, List<Order> children, LocalDateTime now) {
        for (Order child : children) {
            child.cancel(now);
            if (!orderRepository.markCancelled(child.getOrderNo(), now)) {
                throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID,
                        "订单 " + child.getOrderNo() + " 状态已变化，请刷新后重试");
            }
        }
        List<OrderItem> allItems = new ArrayList<>();
        children.forEach(child -> allItems.addAll(orderRepository.findItems(child.getOrderNo())));
        Results.data(productInternalApi.restoreStock(toStockRequest(allItems)));
        orderGroupService.releaseCoupons(group, children);
        children.forEach(this::refundOrderPoints);
        if (!orderGroupRepository.markCancelled(group.getGroupNo(), now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单组状态已变化，请刷新后重试");
        }
        notify(userId, "订单组已取消",
                "合单 " + group.getGroupNo() + " 的 " + children.size() + " 个订单已整组取消，优惠券已退回");
    }

    /** 只归还本单库存（合单部分取消时券不能退，只退库存） */
    private void restoreStockOf(Order order) {
        List<OrderItem> items = orderRepository.findItems(order.getOrderNo());
        Results.data(productInternalApi.restoreStock(toStockRequest(items)));
    }

    /** 取消订单后释放库存与优惠券（按核销明细逐张释放，历史订单退回主券） */
    private void releaseAfterCancel(Order order) {
        restoreStockOf(order);
        List<Long> couponIds = orderRepository.findCoupons(order.getOrderNo()).stream()
                .map(OrderCoupon::getCouponId)
                .distinct()
                .toList();
        if (couponIds.isEmpty() && order.getCouponId() != null) {
            couponIds = List.of(order.getCouponId());
        }
        couponIds.forEach(couponId -> {
            CouponReleaseRequest releaseRequest = new CouponReleaseRequest();
            releaseRequest.setOrderNo(order.getOrderNo());
            Results.data(discountInternalApi.releaseCoupon(couponId, releaseRequest));
        });
    }

    // ==================== 订单组：合单支付 / 查询 ====================

    /** 合单支付成功回调（支付服务调用）：组内待付款子订单整组置为已支付 */
    @Transactional(rollbackFor = Exception.class)
    public void markPaidGroup(String groupNo, PaidRequest request) {
        OrderGroup group = orderGroupService.requireGroup(groupNo);
        if (group.isPaid()) {
            log.info("订单组 {} 已支付，忽略重复回调", groupNo);
            return;
        }
        if (!group.isUnpaid()) {
            throw new BusinessException(OrderErrorCode.ORDER_GROUP_STATUS_INVALID, "订单组当前状态不能支付");
        }
        LocalDateTime paidAt = request.getPaidAt() == null ? LocalDateTime.now() : request.getPaidAt();
        if (orderRepository.markGroupChildrenPaid(groupNo, paidAt) == 0) {
            OrderGroup latest = orderGroupService.requireGroup(groupNo);
            if (latest.isPaid()) {
                return;
            }
            throw new BusinessException(OrderErrorCode.ORDER_GROUP_STATUS_INVALID, "订单组当前状态不能支付");
        }
        if (!orderGroupRepository.markPaid(groupNo, paidAt)) {
            log.warn("订单组 {} 标记支付失败，可能已被并发处理", groupNo);
            return;
        }
        notify(group.getUserId(), "支付成功",
                "合单 " + groupNo + " 支付成功，共 " + group.getShopCount() + " 个订单，商家将尽快为您发货");
    }

    /** 支付服务创建合单支付单前的组信息校验 */
    public OrderGroupInternalDTO getGroupInternal(String groupNo) {
        OrderGroup group = orderGroupService.requireGroup(groupNo);
        OrderGroupInternalDTO dto = new OrderGroupInternalDTO();
        dto.setGroupNo(group.getGroupNo());
        dto.setUserId(group.getUserId());
        dto.setStatus(group.getStatus());
        dto.setShopCount(group.getShopCount());
        dto.setPayAmount(group.getPayAmount());
        dto.setCouponUseOrderNo(group.getCouponUseOrderNo());
        return dto;
    }

    /** 买家查看订单组详情（含子订单与商品明细） */
    public OrderGroupVO groupDetail(Long userId, String groupNo) {
        OrderGroup group = requireOwnedGroup(userId, groupNo);
        List<Order> children = orderRepository.findByGroupNo(groupNo);
        Map<String, List<OrderItem>> itemMap = orderRepository.findItemsByOrderNos(
                children.stream().map(Order::getOrderNo).toList());
        List<OrderVO> childVOs = children.stream()
                .map(child -> toVO(child, itemMap.getOrDefault(child.getOrderNo(), List.of())))
                .toList();
        return toGroupVO(group, childVOs);
    }

    private OrderGroup requireOwnedGroup(Long userId, String groupNo) {
        OrderGroup group = orderGroupService.requireGroup(groupNo);
        if (!group.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        return group;
    }

    private OrderGroupVO toGroupVO(OrderGroup group, List<OrderVO> children) {
        OrderGroupVO vo = new OrderGroupVO();
        vo.setGroupNo(group.getGroupNo());
        vo.setStatus(group.getStatus());
        vo.setStatusText(groupStatusText(group.getStatus()));
        vo.setShopCount(group.getShopCount());
        vo.setOriginalAmount(group.getOriginalAmount());
        vo.setPromotionDiscount(group.getPromotionDiscount());
        vo.setCouponDiscount(group.getCouponDiscount());
        vo.setTotalDiscount(group.getTotalDiscount());
        vo.setMerchantBearAmount(group.getMerchantBearAmount());
        vo.setPlatformBearAmount(group.getPlatformBearAmount());
        vo.setPointsUsed(group.getPointsUsed());
        vo.setPointsDeduction(group.getPointsDeduction());
        vo.setFreightAmount(group.getFreightAmount());
        vo.setPayAmount(group.getPayAmount());
        vo.setPromotionTitle(group.getPromotionTitle());
        vo.setCouponTitle(group.getCouponTitle());
        vo.setReceiverName(group.getReceiverName());
        vo.setReceiverPhone(group.getReceiverPhone());
        vo.setReceiverAddress(group.getReceiverAddress());
        vo.setRemark(group.getRemark());
        vo.setCouponUseOrderNo(group.getCouponUseOrderNo());
        vo.setPayTime(group.getPayTime());
        vo.setCancelTime(group.getCancelTime());
        vo.setClosedTime(group.getClosedTime());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setOrders(children);
        return vo;
    }

    private String groupStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "UNPAID" -> "待付款";
            case "PAID" -> "已支付";
            case "CANCELLED" -> "已取消";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    /** 调用支付服务模拟退款，未找到支付记录时视为无需退款 */
    private RefundResultVO refundOrder(Order order, String reason) {
        RefundRequest request = new RefundRequest();
        request.setOrderNo(order.getOrderNo());
        if (StringUtils.hasText(order.getGroupNo())) {
            // 合单共用一张支付单：按子单分摊后的实付金额做部分退款
            request.setGroupNo(order.getGroupNo());
            request.setAmount(order.getPayAmount());
        }
        request.setReason(reason);
        try {
            RefundResultVO result = Results.data(paymentInternalApi.refund(request));
            return result == null ? RefundResultVO.failure("退款服务未返回结果") : result;
        } catch (Exception ex) {
            log.error("调用支付服务退款失败: orderNo={}", order.getOrderNo(), ex);
            return RefundResultVO.failure("退款服务暂不可用");
        }
    }

    public void confirm(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        LocalDateTime now = LocalDateTime.now();
        order.complete(now);
        if (!orderRepository.markCompleted(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        grantPoints(order, "订单完成奖励");
        settleCommission(order);
        notify(order.getUserId(), "交易完成",
                "订单 " + orderNo + " 已确认收货，感谢您的购买");
    }

    /**
     * 商家发货：订单服务统一编排——先标记已发货，再创建物流运单。
     * 运单创建失败时整体回滚，避免"订单已发货却无运单/重复运单"的不一致。
     * <p>发货只允许订单所属店铺的商家操作：shopId 为空（平台方等无店铺归属的账号）同样拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long shopId, String orderNo, Long companyId) {
        Order order = requireOrder(orderNo);
        if (shopId == null || !shopId.equals(order.getShopId())) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN, "只有订单所属商家可以发货");
        }
        if (companyId == null) {
            throw new BusinessException(OrderErrorCode.SHIPMENT_FAILED, "请选择物流公司");
        }
        LocalDateTime now = LocalDateTime.now();
        order.ship(now);
        if (!orderRepository.markShipped(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "只有待发货订单可以发货");
        }

        LogisticsDtos.ShipmentCreateRequest request = new LogisticsDtos.ShipmentCreateRequest();
        request.setOrderNo(order.getOrderNo());
        request.setCompanyId(companyId);
        request.setShopId(order.getShopId());
        request.setShopName(order.getShopName());
        request.setReceiverName(order.getReceiverName());
        request.setReceiverPhone(order.getReceiverPhone());
        request.setReceiverAddress(order.getReceiverAddress());
        request.setItemsText(orderRepository.findItems(orderNo).stream()
                .map(item -> item.getProductName() + " ×" + item.getQuantity())
                .reduce((a, b) -> a + "、" + b).orElse(""));
        try {
            MerchantDtos.ShopVO shop = Results.data(merchantInternalApi.shop(order.getShopId()));
            if (shop != null) {
                request.setPickupAddress(shop.getAddress() == null ? "" : shop.getAddress());
                request.setPickupRegion(shop.getRegion());
            }
        } catch (Exception ex) {
            log.error("发货读取店铺信息失败: orderNo={}", orderNo, ex);
            throw new BusinessException(OrderErrorCode.SHIPMENT_FAILED);
        }
        try {
            LogisticsDtos.ShipmentVO shipment = Results.data(logisticsInternalApi.createShipment(request));
            if (shipment == null || !StringUtils.hasText(shipment.getTrackingNo())) {
                throw new BusinessException(OrderErrorCode.SHIPMENT_FAILED);
            }
            log.info("正向运单已创建: orderNo={}, trackingNo={}", orderNo, shipment.getTrackingNo());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("创建物流运单失败: orderNo={}", orderNo, ex);
            throw new BusinessException(OrderErrorCode.SHIPMENT_FAILED);
        }
        notify(order.getUserId(), "商品已发货",
                "您的订单 " + orderNo + "（" + order.getShopName() + "）已发货，请注意查收");
    }

    /** 物流正向签收回调：记录签收时间并通知买家，签收满 N 天自动确认收货 */
    @Transactional(rollbackFor = Exception.class)
    public void onShipmentSigned(OrderInternalDtos.ShipmentSignedRequest request) {
        Order order = requireOrder(request.getOrderNo());
        if (order.getSignedTime() != null) {
            return; // 重复回调幂等处理
        }
        LocalDateTime signedAt = request.getSignedAt() == null ? LocalDateTime.now() : request.getSignedAt();
        // 仅"待收货"订单记录签收时间：已确认/售后中/已退款订单无需联动
        if (!orderRepository.markSigned(order.getOrderNo(), signedAt)) {
            return;
        }
        notify(order.getUserId(), "包裹已签收",
                "订单 " + order.getOrderNo() + "（" + order.getShopName() + "）已签收，"
                        + AUTO_COMPLETE_DAYS + " 天内未确认将自动完成交易，您也可以手动确认收货");
    }

    /** 定时任务：物流签收满 AUTO_COMPLETE_DAYS 天仍未确认收货的订单自动完成 */
    public void autoCompleteSignedOrders() {
        List<Order> dueOrders = orderRepository.findSignedExpired(LocalDateTime.now().minusDays(AUTO_COMPLETE_DAYS));
        for (Order order : dueOrders) {
            try {
                LocalDateTime now = LocalDateTime.now();
                order.complete(now);
                if (orderRepository.markCompleted(order.getOrderNo(), now)) {
                    grantPoints(order, "订单完成奖励");
                    settleCommission(order);
                    notify(order.getUserId(), "交易完成",
                            "订单 " + order.getOrderNo() + " 签收已满 " + AUTO_COMPLETE_DAYS + " 天，已自动确认收货");
                }
            } catch (Exception ex) {
                log.warn("签收自动完成订单失败: orderNo={}", order.getOrderNo(), ex);
            }
        }
    }

    /** 订单完成后按实付金额发放积分（配置 points-rule/earn-amount-per-point），失败不影响交易 */
    private void grantPoints(Order order, String remark) {
        // 0 元单（积分兑换订单等）不发积分，避免兑换循环
        if (order.getPayAmount() == null || order.getPayAmount().signum() <= 0) {
            return;
        }
        try {
            MemberDtos.EarnPointsRequest request = new MemberDtos.EarnPointsRequest();
            request.setUserId(order.getUserId());
            request.setAmount(order.getPayAmount());
            request.setRemark(remark + "：" + order.getOrderNo());
            request.setBizNo(order.getOrderNo());
            Integer gain = Results.data(memberInternalApi.earn(request));
            if (gain != null && gain > 0) {
                notify(order.getUserId(), "积分到账",
                        "订单 " + order.getOrderNo() + " 交易完成，获得 " + gain + " 积分");
            }
        } catch (Exception ex) {
            log.warn("订单积分发放失败: orderNo={}", order.getOrderNo(), ex);
        }
    }

    /** 订单完成计佣。计佣失败不影响交易完成，只记日志（流水幂等，可事后补偿） */
    private void settleCommission(Order order) {
        try {
            commissionApplicationService.settle(order);
        } catch (Exception ex) {
            log.warn("订单计佣失败: orderNo={}", order.getOrderNo(), ex);
        }
    }

    /**
     * 积分商城已改为兑换优惠券，兑换商品订单链路下线（2026-11）。
     */
    public OrderInternalDTO getInternal(String orderNo) {
        Order order = requireOrder(orderNo);
        OrderInternalDTO dto = new OrderInternalDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setStatus(order.getStatus());
        dto.setBizType(order.getBizType());
        dto.setGroupNo(order.getGroupNo());
        dto.setPayAmount(order.getPayAmount());
        return dto;
    }

    public boolean hasUnpaidRecovery(Long userId) {
        return orderRepository.hasUnpaidRecovery(userId);
    }

    public void markPaid(String orderNo, PaidRequest request) {
        Order order = requireOrder(orderNo);
        if (order.isPaid()) {
            log.info("订单 {} 已支付，忽略重复回调", orderNo);
            return;
        }
        LocalDateTime paidAt = request.getPaidAt() == null ? LocalDateTime.now() : request.getPaidAt();
        order.pay(paidAt);
        if (!orderRepository.markPaid(orderNo, paidAt)) {
            Order latest = requireOrder(orderNo);
            if (latest.isPaid()) {
                return;
            }
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单当前状态不能支付");
        }
        notify(order.getUserId(), "支付成功",
                "订单 " + orderNo + " 支付成功，商家将尽快为您发货");
    }

    public List<Long> purchasedProductIds(Long userId) {
        return orderRepository.purchasedProductIds(userId);
    }

    public UserStatsVO userStats(Long userId) {
        UserStats stats = orderRepository.userStats(userId);
        UserStatsVO vo = new UserStatsVO();
        vo.setOrderCount(stats.orderCount());
        vo.setTotalSpent(stats.totalSpent());
        vo.setLastOrderAt(stats.lastOrderAt());
        return vo;
    }

    // ==================== 内部逻辑 ====================

    /** 推送站内通知，失败不影响主流程 */
    private void notify(Long userId, String title, String content) {
        try {
            MessageDtos.NoticeRequest request = new MessageDtos.NoticeRequest();
            request.setUserId(userId);
            request.setTitle(title);
            request.setContent(content);
            messageInternalApi.pushNotice(request);
        } catch (Exception ex) {
            log.warn("推送站内通知失败: userId={}, title={}, cause={}", userId, title, ex.getMessage());
        }
    }

    private PreparedOrder prepare(Long userId, List<OrderItemRequest> itemRequests, List<Long> couponIds,
                                  List<Long> promotionIds, boolean usePoints) {
        // 售后申诉成立后生成的补款订单未支付前，禁止其他购买操作
        if (orderRepository.hasUnpaidRecovery(userId)) {
            throw new BusinessException(OrderErrorCode.UNPAID_RECOVERY_EXISTS);
        }
        Map<String, OrderItemRequest> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : itemRequests) {
            Long skuId = item.getSkuId() == null ? 0L : item.getSkuId();
            String key = item.getProductId() + ":" + skuId;
            merged.merge(key, item, (a, b) -> {
                a.setQuantity(a.getQuantity() + b.getQuantity());
                return a;
            });
        }

        List<Long> productIds = merged.values().stream().map(OrderItemRequest::getProductId)
                .distinct().toList();
        ProductBatchRequest batchRequest = new ProductBatchRequest();
        batchRequest.setIds(new ArrayList<>(productIds));
        List<ProductVO> products = Results.data(productInternalApi.batch(batchRequest));
        Map<Long, ProductVO> productMap = products == null ? Collections.emptyMap()
                : products.stream().collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        List<PreparedItem> allItems = new ArrayList<>();
        List<DiscountItem> discountItems = new ArrayList<>();
        List<StockChangeRequest.StockItem> stockItems = new ArrayList<>();
        BigDecimal originalAmount = ZERO;

        for (OrderItemRequest itemRequest : merged.values()) {
            ProductVO product = productMap.get(itemRequest.getProductId());
            if (product == null) {
                throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND,
                        "商品 " + itemRequest.getProductId() + " 不存在");
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BusinessException(OrderErrorCode.PRODUCT_OFF_SHELF, product.getName() + " 已下架");
            }
            // 防刷评价：商家不能购买自己店铺的商品
            Long myShopId = UserContext.getShopId();
            if (myShopId != null && myShopId.equals(product.getShopId())) {
                throw new BusinessException(OrderErrorCode.CANNOT_BUY_OWN_PRODUCT);
            }
            Long skuId = itemRequest.getSkuId() == null ? 0L : itemRequest.getSkuId();
            ProductVO.SkuVO sku = findSku(product, skuId);

            int quantity = itemRequest.getQuantity();
            int stock = sku != null && sku.getStock() != null ? sku.getStock()
                    : (product.getStock() == null ? 0 : product.getStock());
            if (stock < quantity) {
                throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, product.getName() + " 库存不足");
            }

            BigDecimal price = sku != null ? sku.getPrice() : product.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            originalAmount = originalAmount.add(subtotal);
            allItems.add(new PreparedItem(product, sku, skuId, quantity, subtotal, null));

            DiscountItem discountItem = new DiscountItem();
            discountItem.setProductId(product.getId());
            discountItem.setCategoryId(product.getCategoryId());
            discountItem.setShopId(product.getShopId());
            discountItem.setPrice(price);
            discountItem.setQuantity(quantity);
            discountItems.add(discountItem);

            StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
            stockItem.setProductId(product.getId());
            stockItem.setSkuId(skuId > 0 ? skuId : null);
            stockItem.setQuantity(quantity);
            stockItems.add(stockItem);
        }

        DiscountCalculateRequest discountRequest = new DiscountCalculateRequest();
        discountRequest.setUserId(userId);
        discountRequest.setCouponIds(couponIds);
        discountRequest.setPromotionIds(promotionIds);
        discountRequest.setItems(discountItems);
        DiscountResultVO discount = Results.data(discountInternalApi.calculate(discountRequest));

        // 行级优惠明细与商品行按序对齐（discountItems 与 allItems 同循环构建，顺序一致）：
        // 供逐商品展示「原价→优惠后」与订单行落库；明细缺失（旧返回/兜底）时行级字段保持零值
        List<DiscountResultVO.ItemDiscount> discountRows = discount.getItemDiscounts();
        if (discountRows != null && discountRows.size() == allItems.size()) {
            List<PreparedItem> baseItems = allItems;
            allItems = IntStream.range(0, baseItems.size())
                    .mapToObj(i -> baseItems.get(i).withDiscountRow(discountRows.get(i)))
                    .toList();
        }

        // 积分抵现：在活动/券算完后对应付金额抵扣，抵扣额计入平台承担（平台补贴）。
        // 佣金基数 = 实付，抵现部分天然不计佣；取消/退款按单退回积分。
        PointsDeduction deduction = resolvePointsDeduction(userId, nz(discount.getPayAmount()), usePoints);
        if (deduction.deductionAmount().signum() > 0) {
            discount.setPayAmount(nz(discount.getPayAmount()).subtract(deduction.deductionAmount()));
            discount.setPlatformBearAmount(nz(discount.getPlatformBearAmount()).add(deduction.deductionAmount()));
        }

        Map<Long, List<PreparedItem>> byShop = new LinkedHashMap<>();
        for (PreparedItem item : allItems) {
            byShop.computeIfAbsent(item.product.getShopId(), k -> new ArrayList<>()).add(item);
        }
        List<PreparedShop> shops = byShop.entrySet().stream().map(entry -> {
            BigDecimal shopOriginal = entry.getValue().stream()
                    .map(PreparedItem::subtotal)
                    .reduce(ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            String shopName = entry.getValue().get(0).product.getShopName();
            return new PreparedShop(entry.getKey(), shopName, entry.getValue(), shopOriginal);
        }).toList();

        return new PreparedOrder(allItems, shops, originalAmount, discount, stockItems,
                deduction.pointsUsed(), deduction.deductionAmount(),
                deduction.pointsAvailable(), deduction.pointsPerYuan());
    }

    // ==================== 积分抵现 ====================

    /** 结算时的积分抵现结果 */
    private record PointsDeduction(int pointsUsed, BigDecimal deductionAmount,
                                   int pointsAvailable, int pointsPerYuan) {
    }

    /**
     * 按「min(账户余额, 应付 × 上限百分比 × 每元积分)」自动算最优抵扣（勾选抵现时）。
     * 抵现未开启（比例/上限未配置）或应付为 0 时不可抵。
     */
    private PointsDeduction resolvePointsDeduction(Long userId, BigDecimal payAmount, boolean usePoints) {
        int pointsPerYuan = pointsRuleReader.pointsPerYuan();
        int maxPercent = pointsRuleReader.maxDeductPercent();
        int balance = queryPointsBalance(userId);
        if (!usePoints || pointsPerYuan <= 0 || maxPercent <= 0
                || payAmount.signum() <= 0 || balance <= 0) {
            return new PointsDeduction(0, ZERO, balance, pointsPerYuan);
        }
        BigDecimal maxMoney = payAmount.multiply(BigDecimal.valueOf(maxPercent))
                .divide(HUNDRED, 2, RoundingMode.DOWN);
        int maxPoints = maxMoney.multiply(BigDecimal.valueOf(pointsPerYuan))
                .setScale(0, RoundingMode.DOWN).intValue();
        int pointsUsed = Math.min(balance, Math.max(maxPoints, 0));
        if (pointsUsed <= 0) {
            return new PointsDeduction(0, ZERO, balance, pointsPerYuan);
        }
        BigDecimal deductionAmount = BigDecimal.valueOf(pointsUsed)
                .divide(BigDecimal.valueOf(pointsPerYuan), 2, RoundingMode.DOWN)
                .min(maxMoney);
        return new PointsDeduction(pointsUsed, deductionAmount, balance, pointsPerYuan);
    }

    private int queryPointsBalance(Long userId) {
        try {
            Integer balance = Results.data(memberInternalApi.balance(userId));
            return balance == null ? 0 : Math.max(balance, 0);
        } catch (Exception ex) {
            log.warn("查询积分余额失败，本次结算不使用积分抵现: userId={}, cause={}", userId, ex.getMessage());
            return 0;
        }
    }

    /** 退回该订单抵现扣减的积分，按订单号幂等；失败只记日志，不影响取消/退款主流程 */
    private void refundOrderPoints(Order order) {
        if (order.getPointsUsed() == null || order.getPointsUsed() <= 0) {
            return;
        }
        refundPointsQuietly(order.getUserId(), order.getOrderNo(), "订单取消/退款退回抵现积分");
    }

    private void refundPointsQuietly(Long userId, String bizNo, String remark) {
        try {
            MemberDtos.RefundSpendRequest request = new MemberDtos.RefundSpendRequest();
            request.setUserId(userId);
            request.setBizNo(bizNo);
            request.setRemark(remark);
            Integer refunded = Results.data(memberInternalApi.refundSpend(request));
            if (refunded != null && refunded > 0) {
                notify(userId, "积分退回",
                        "已退回抵现使用的 " + refunded + " 积分（单号 " + bizNo + "）");
            }
        } catch (Exception ex) {
            log.error("退回抵现积分失败，需要人工处理: userId={}, bizNo={}", userId, bizNo, ex);
        }
    }

    /** 按店铺小计比例分摊优惠与费用承担（含积分抵现），尾差给最后一个店铺 */
    private List<AllocatedShop> allocateShops(PreparedOrder prepared) {
        List<PreparedShop> shops = prepared.shops;
        BigDecimal totalOriginal = prepared.originalAmount;
        BigDecimal totalPay = prepared.discount.getPayAmount();
        BigDecimal totalPromotion = prepared.discount.getPromotionDiscount() == null
                ? ZERO : prepared.discount.getPromotionDiscount();
        BigDecimal totalMerchantBear = prepared.discount.getMerchantBearAmount() == null
                ? ZERO : prepared.discount.getMerchantBearAmount();
        BigDecimal totalPlatformBear = prepared.discount.getPlatformBearAmount() == null
                ? ZERO : prepared.discount.getPlatformBearAmount();
        BigDecimal totalPointsDeduction = prepared.pointsDeduction == null ? ZERO : prepared.pointsDeduction;
        int totalPointsUsed = prepared.pointsUsed;

        List<AllocatedShop> result = new ArrayList<>();

        // 行级优惠明细齐全（真实算价路径）：店铺级促销/券直接取该店各行之和——引擎已把优惠
        // 精确摊到命中行，不会像按比例分摊那样把优惠扩散到没有活动的店铺；活动费用承担按
        // 各店活动优惠额占比分摊，抵现按店铺小计占比分摊，尾差给最后一家；应付按店铺推导，
        // 合计与总额严格相等（各行明细之和 = 引擎总额）。
        boolean rowsComplete = shops.stream().flatMap(shop -> shop.items().stream())
                .allMatch(item -> item.discountRow() != null);
        if (rowsComplete) {
            BigDecimal allocatedMerchantBear = ZERO;
            BigDecimal allocatedPlatformBear = ZERO;
            BigDecimal allocatedPointsDeduction = ZERO;
            int allocatedPointsUsed = 0;
            for (int i = 0; i < shops.size(); i++) {
                PreparedShop shop = shops.get(i);
                boolean last = i == shops.size() - 1;
                BigDecimal promotion = shop.items().stream().map(PreparedItem::rowPromotionDiscount)
                        .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
                BigDecimal coupon = shop.items().stream().map(PreparedItem::rowCouponDiscount)
                        .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
                BigDecimal ratio = totalOriginal.signum() == 0 ? BigDecimal.ZERO
                        : shop.originalAmount.divide(totalOriginal, 6, RoundingMode.HALF_UP);
                BigDecimal pointsDeduction = last
                        ? totalPointsDeduction.subtract(allocatedPointsDeduction).setScale(2, RoundingMode.HALF_UP)
                        : totalPointsDeduction.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                int pointsUsed = last
                        ? totalPointsUsed - allocatedPointsUsed
                        : BigDecimal.valueOf(totalPointsUsed).multiply(ratio)
                        .setScale(0, RoundingMode.DOWN).intValue();
                BigDecimal bearRatio = totalPromotion.signum() == 0 ? BigDecimal.ZERO
                        : promotion.divide(totalPromotion, 6, RoundingMode.HALF_UP);
                BigDecimal merchantBear = last
                        ? totalMerchantBear.subtract(allocatedMerchantBear).setScale(2, RoundingMode.HALF_UP)
                        : totalMerchantBear.multiply(bearRatio).setScale(2, RoundingMode.HALF_UP);
                BigDecimal platformBear = last
                        ? totalPlatformBear.subtract(allocatedPlatformBear).setScale(2, RoundingMode.HALF_UP)
                        : totalPlatformBear.multiply(bearRatio).setScale(2, RoundingMode.HALF_UP);
                allocatedMerchantBear = allocatedMerchantBear.add(merchantBear);
                allocatedPlatformBear = allocatedPlatformBear.add(platformBear);
                allocatedPointsDeduction = allocatedPointsDeduction.add(pointsDeduction);
                allocatedPointsUsed += pointsUsed;
                BigDecimal pay = shop.originalAmount.subtract(promotion).subtract(coupon)
                        .subtract(pointsDeduction).setScale(2, RoundingMode.HALF_UP);
                result.add(new AllocatedShop(shop.shopId, shop.shopName, shop.originalAmount,
                        promotion, coupon, merchantBear, platformBear, pay,
                        pointsUsed, pointsDeduction, shop.items));
            }
            return result;
        }

        BigDecimal allocatedPay = ZERO;
        BigDecimal allocatedPromotion = ZERO;
        BigDecimal allocatedMerchantBear = ZERO;
        BigDecimal allocatedPlatformBear = ZERO;
        BigDecimal allocatedPointsDeduction = ZERO;
        int allocatedPointsUsed = 0;

        for (int i = 0; i < shops.size(); i++) {
            PreparedShop shop = shops.get(i);
            boolean last = i == shops.size() - 1;

            BigDecimal pay;
            BigDecimal promotion;
            BigDecimal merchantBear;
            BigDecimal platformBear;
            BigDecimal pointsDeduction;
            int pointsUsed;
            if (last) {
                pay = totalPay.subtract(allocatedPay).setScale(2, RoundingMode.HALF_UP);
                promotion = totalPromotion.subtract(allocatedPromotion).setScale(2, RoundingMode.HALF_UP);
                merchantBear = totalMerchantBear.subtract(allocatedMerchantBear).setScale(2, RoundingMode.HALF_UP);
                platformBear = totalPlatformBear.subtract(allocatedPlatformBear).setScale(2, RoundingMode.HALF_UP);
                pointsDeduction = totalPointsDeduction.subtract(allocatedPointsDeduction)
                        .setScale(2, RoundingMode.HALF_UP);
                pointsUsed = totalPointsUsed - allocatedPointsUsed;
            } else {
                BigDecimal ratio = totalOriginal.signum() == 0 ? BigDecimal.ZERO
                        : shop.originalAmount.divide(totalOriginal, 6, RoundingMode.HALF_UP);
                pay = totalPay.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                promotion = totalPromotion.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                merchantBear = totalMerchantBear.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                platformBear = totalPlatformBear.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                pointsDeduction = totalPointsDeduction.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                pointsUsed = BigDecimal.valueOf(totalPointsUsed).multiply(ratio)
                        .setScale(0, RoundingMode.DOWN).intValue();
                allocatedPay = allocatedPay.add(pay);
                allocatedPromotion = allocatedPromotion.add(promotion);
                allocatedMerchantBear = allocatedMerchantBear.add(merchantBear);
                allocatedPlatformBear = allocatedPlatformBear.add(platformBear);
                allocatedPointsDeduction = allocatedPointsDeduction.add(pointsDeduction);
                allocatedPointsUsed += pointsUsed;
            }
            BigDecimal coupon = shop.originalAmount.subtract(pay).subtract(promotion);
            if (coupon.signum() < 0) {
                coupon = ZERO;
            }
            result.add(new AllocatedShop(shop.shopId, shop.shopName, shop.originalAmount,
                    promotion, coupon, merchantBear, platformBear, pay,
                    pointsUsed, pointsDeduction, shop.items));
        }
        return result;
    }

    private List<OrderPreviewVO.ShopPreviewVO> buildShopPreviews(PreparedOrder prepared) {
        return allocateShops(prepared).stream().map(allocated -> {
            OrderPreviewVO.ShopPreviewVO vo = new OrderPreviewVO.ShopPreviewVO();
            vo.setShopId(allocated.shopId);
            vo.setShopName(allocated.shopName);
            vo.setOriginalAmount(allocated.originalAmount);
            vo.setPromotionDiscount(allocated.promotionDiscount);
            vo.setCouponDiscount(allocated.couponDiscount);
            vo.setFreightAmount(FREIGHT_AMOUNT);
            vo.setPayAmount(allocated.payAmount);
            vo.setItems(allocated.items.stream().map(this::toPreviewItemVO).toList());
            return vo;
        }).toList();
    }

    private PageResult<OrderVO> assemblePage(PageResult<Order> result) {
        Map<String, List<OrderItem>> itemMap = orderRepository.findItemsByOrderNos(
                result.getList().stream().map(Order::getOrderNo).toList());
        List<OrderVO> records = result.getList().stream()
                .map(order -> toVO(order, itemMap.getOrDefault(order.getOrderNo(), List.of())))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
    }

    private OrderVO toVO(Order order) {
        return toVO(order, orderRepository.findItems(order.getOrderNo()));
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setShopId(order.getShopId());
        vo.setShopName(order.getShopName());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.textOf(order.getStatus()));
        vo.setBizType(order.getBizType() == null ? "NORMAL" : order.getBizType());
        vo.setBizTypeText(order.isRecovery() ? "补款订单" : "普通订单");
        vo.setSourceOrderNo(order.getSourceOrderNo());
        vo.setGroupNo(order.getGroupNo());
        vo.setOriginalAmount(order.getOriginalAmount());
        vo.setPromotionDiscount(order.getPromotionDiscount());
        vo.setCouponDiscount(order.getCouponDiscount());
        vo.setMerchantBearAmount(order.getMerchantBearAmount());
        vo.setPlatformBearAmount(order.getPlatformBearAmount());
        vo.setPointsUsed(order.getPointsUsed());
        vo.setPointsDeduction(order.getPointsDeduction());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setPromotionTitle(order.getPromotionTitle());
        vo.setCouponTitle(order.getCouponTitle());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setCompleteTime(order.getCompleteTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setRefundTime(order.getRefundTime());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    private Order requireOwned(Long userId, String orderNo) {
        Order order = requireOrder(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        return order;
    }

    private Order requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private ProductVO.SkuVO findSku(ProductVO product, Long skuId) {
        if (skuId == null || skuId == 0) {
            return null;
        }
        ProductVO.SkuVO sku = product.getSkus() == null ? null : product.getSkus().stream()
                .filter(s -> skuId.equals(s.getId()))
                .findFirst()
                .orElse(null);
        if (sku == null) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND, "商品规格不存在");
        }
        return sku;
    }

    private StockChangeRequest toStockRequest(List<OrderItem> items) {
        // 按（商品, SKU）聚合：带规格的商品库存按 SKU 归还
        record ItemKey(Long productId, Long skuId) {
        }
        Map<ItemKey, Integer> merged = new LinkedHashMap<>();
        for (OrderItem item : items) {
            merged.merge(new ItemKey(item.getProductId(), item.getSkuId()), item.getQuantity(), Integer::sum);
        }
        StockChangeRequest request = new StockChangeRequest();
        request.setItems(merged.entrySet().stream().map(entry -> {
            StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
            stockItem.setProductId(entry.getKey().productId());
            stockItem.setSkuId(entry.getKey().skuId());
            stockItem.setQuantity(entry.getValue());
            return stockItem;
        }).toList());
        return request;
    }

    /** 折扣服务返回的券核销明细 → 预览/落库结构 */
    private List<OrderPreviewVO.CouponUsageVO> toCouponUsages(DiscountResultVO discount) {
        if (discount.getAppliedCoupons() == null) {
            return List.of();
        }
        return discount.getAppliedCoupons().stream().map(usage -> {
            OrderPreviewVO.CouponUsageVO vo = new OrderPreviewVO.CouponUsageVO();
            vo.setCouponId(usage.getCouponId());
            vo.setTitle(usage.getTitle());
            vo.setDiscount(usage.getDiscount());
            return vo;
        }).toList();
    }

    private OrderPreviewItemVO toPreviewItemVO(PreparedItem item) {
        OrderPreviewItemVO vo = new OrderPreviewItemVO();
        vo.setProductId(item.product.getId());
        vo.setSkuId(item.skuId);
        vo.setSkuSpecs(item.sku == null ? "" : specsText(item.sku.getSpecs()));
        vo.setProductName(item.product.getName());
        vo.setProductEmoji(item.product.getEmoji());
        vo.setProductColor(item.product.getColor());
        vo.setProductCoverImage(item.product.getCoverImage());
        vo.setShopId(item.product.getShopId());
        vo.setShopName(item.product.getShopName());
        vo.setCategoryId(item.product.getCategoryId());
        vo.setPrice(item.sku != null ? item.sku.getPrice() : item.product.getPrice());
        vo.setQuantity(item.quantity);
        vo.setSubtotal(item.subtotal);
        fillItemDiscount(vo, item.discountRow());
        return vo;
    }

    /** 逐商品优惠拆解：原价小计 / 活动、券各减多少、参与了哪些优惠 / 优惠后小计（明细缺失时保持零值） */
    private void fillItemDiscount(OrderPreviewItemVO vo, DiscountResultVO.ItemDiscount row) {
        if (row == null) {
            vo.setPromotionDiscount(ZERO);
            vo.setCouponDiscount(ZERO);
            vo.setFinalSubtotal(vo.getSubtotal());
            vo.setItemPromotions(List.of());
            vo.setItemCoupons(List.of());
            return;
        }
        vo.setPromotionDiscount(nz(row.getPromotionDiscount()));
        vo.setCouponDiscount(nz(row.getCouponDiscount()));
        vo.setFinalSubtotal(row.getFinalSubtotal() == null
                ? vo.getSubtotal().subtract(vo.getPromotionDiscount()).subtract(vo.getCouponDiscount())
                : row.getFinalSubtotal());
        vo.setItemPromotions(toAppliedHits(row.getPromotions()));
        vo.setItemCoupons(toAppliedHits(row.getCoupons()));
    }

    private List<OrderPreviewItemVO.AppliedHitVO> toAppliedHits(List<DiscountResultVO.AppliedHit> hits) {
        if (hits == null) {
            return List.of();
        }
        return hits.stream().map(hit -> {
            OrderPreviewItemVO.AppliedHitVO vo = new OrderPreviewItemVO.AppliedHitVO();
            vo.setId(hit.getId());
            vo.setTitle(hit.getTitle());
            vo.setAmount(hit.getAmount());
            return vo;
        }).toList();
    }

    /** 结算页可勾选的活动选项（null 安全：旧返回/兜底场景无该字段时不展示选择区） */
    private List<OrderPreviewVO.PromotionOptionVO> toPromotionOptions(List<DiscountResultVO.PromotionOption> options) {
        if (options == null) {
            return List.of();
        }
        return options.stream().map(option -> {
            OrderPreviewVO.PromotionOptionVO vo = new OrderPreviewVO.PromotionOptionVO();
            vo.setPromotionId(option.getPromotionId());
            vo.setTitle(option.getTitle());
            vo.setShopId(option.getShopId());
            vo.setShopName(option.getShopName());
            vo.setScopeText(option.getScopeText());
            vo.setRuleText(option.getRuleText());
            vo.setDiscount(option.getDiscount());
            vo.setCouponStackable(option.getCouponStackable());
            vo.setChosen(option.getChosen());
            return vo;
        }).toList();
    }

    private OrderItem toOrderItem(Order order, PreparedItem item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setOrderNo(order.getOrderNo());
        orderItem.setProductId(item.product.getId());
        // 快照类目，佣金结算按行取费率；平台事后改商品类目也不影响已下单的订单
        orderItem.setCategoryId(item.product.getCategoryId());
        orderItem.setSkuId(item.skuId);
        orderItem.setSkuSpecs(item.sku == null ? "" : specsText(item.sku.getSpecs()));
        orderItem.setProductName(item.product.getName());
        orderItem.setProductEmoji(item.product.getEmoji());
        orderItem.setProductColor(item.product.getColor());
        orderItem.setProductCoverImage(item.product.getCoverImage());
        orderItem.setShopId(item.product.getShopId());
        orderItem.setShopName(item.product.getShopName());
        orderItem.setPrice(item.sku != null ? item.sku.getPrice() : item.product.getPrice());
        orderItem.setQuantity(item.quantity);
        orderItem.setSubtotal(item.subtotal);
        // 行级优惠快照：与引擎逐商品明细一致（明细缺失时为零）
        orderItem.setPromotionDiscount(item.rowPromotionDiscount());
        orderItem.setCouponDiscount(item.rowCouponDiscount());
        return orderItem;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setSkuId(item.getSkuId());
        vo.setSkuSpecs(item.getSkuSpecs());
        vo.setProductName(item.getProductName());
        vo.setProductEmoji(item.getProductEmoji());
        vo.setProductColor(item.getProductColor());
        vo.setProductCoverImage(item.getProductCoverImage());
        vo.setShopId(item.getShopId());
        vo.setShopName(item.getShopName());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        vo.setPromotionDiscount(nz(item.getPromotionDiscount()));
        vo.setCouponDiscount(nz(item.getCouponDiscount()));
        return vo;
    }

    private String specsText(Map<String, String> specs) {
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        return specs.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(" / "));
    }

    /** 结算单行：discountRow 为引擎返回的该行优惠拆解（原价小计/活动/券/优惠后），可能为 null（明细缺失时） */
    private record PreparedItem(ProductVO product, ProductVO.SkuVO sku, Long skuId,
                                int quantity, BigDecimal subtotal,
                                DiscountResultVO.ItemDiscount discountRow) {

        PreparedItem withDiscountRow(DiscountResultVO.ItemDiscount row) {
            return new PreparedItem(product, sku, skuId, quantity, subtotal, row);
        }

        BigDecimal rowPromotionDiscount() {
            return discountRow == null || discountRow.getPromotionDiscount() == null
                    ? ZERO : discountRow.getPromotionDiscount();
        }

        BigDecimal rowCouponDiscount() {
            return discountRow == null || discountRow.getCouponDiscount() == null
                    ? ZERO : discountRow.getCouponDiscount();
        }
    }

    private record PreparedShop(Long shopId, String shopName, List<PreparedItem> items,
                                BigDecimal originalAmount) {
    }

    private record PreparedOrder(List<PreparedItem> allItems,
                                 List<PreparedShop> shops,
                                 BigDecimal originalAmount,
                                 DiscountResultVO discount,
                                 List<StockChangeRequest.StockItem> stockItems,
                                 int pointsUsed,
                                 BigDecimal pointsDeduction,
                                 int pointsAvailable,
                                 int pointsPerYuan) {
    }

    private record AllocatedShop(Long shopId, String shopName, BigDecimal originalAmount,
                                 BigDecimal promotionDiscount, BigDecimal couponDiscount,
                                 BigDecimal merchantBearAmount, BigDecimal platformBearAmount,
                                 BigDecimal payAmount, Integer pointsUsed, BigDecimal pointsDeduction,
                                 List<PreparedItem> items) {
    }
}
