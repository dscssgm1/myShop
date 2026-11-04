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
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.model.UserStats;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.order.infrastructure.support.OrderNoGenerator;
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

/**
 * 订单应用服务：下单时按店铺拆分，每个店铺生成一个独立订单（一店一单）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal FREIGHT_AMOUNT = ZERO;
    /** 物流签收后买家未手动确认，自动完成订单的天数 */
    private static final int AUTO_COMPLETE_DAYS = 7;
    /** 积分商城兑换订单 */
    private static final String BIZ_TYPE_POINTS = "POINTS";

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

    // ==================== 预览 ====================

    public OrderPreviewVO preview(Long userId, OrderPreviewRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponIds());
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
        return vo;
    }

    // ==================== 创建订单（一店一单） ====================

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO create(Long userId, OrderCreateRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponIds());
        List<AllocatedShop> allocations = allocateShops(prepared);
        List<DiscountResultVO.CouponUsage> couponUsages = prepared.discount.getAppliedCoupons() == null
                ? List.of() : prepared.discount.getAppliedCoupons();

        // 跨店铺也允许用券：券核销明细会写入每张子订单，取消任一子订单按 orderNo 幂等释放一次
        List<CreateOrderVO.OrderBrief> briefs = new ArrayList<>();
        for (AllocatedShop allocated : allocations) {
            String orderNo = orderNoGenerator.next();
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setShopId(allocated.shopId);
            order.setShopName(allocated.shopName);
            order.setStatus(OrderStatus.UNPAID.name());
            order.setBizType("NORMAL");
            order.setOriginalAmount(allocated.originalAmount);
            order.setPromotionDiscount(allocated.promotionDiscount);
            order.setCouponDiscount(allocated.couponDiscount);
            order.setMerchantBearAmount(allocated.merchantBearAmount);
            order.setPlatformBearAmount(allocated.platformBearAmount);
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

        // 扣减库存（按商品聚合）
        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setItems(prepared.stockItems);
        Results.data(productInternalApi.deductStock(stockRequest));

        // 逐张核销优惠券（订单号取第一张子订单），失败释放已核销的券并回滚库存
        if (!couponUsages.isEmpty()) {
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
            } catch (RuntimeException ex) {
                log.warn("核销优惠券失败，回滚库存与已核销的券", ex);
                usedCoupons.forEach(couponId ->
                        releaseQuietly(couponId, orderNo));
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
                        + "，应付 ¥" + brief.getPayAmount()));

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrders(briefs);
        vo.setOrderNo(briefs.get(0).getOrderNo());
        vo.setPayAmount(briefs.stream().map(CreateOrderVO.OrderBrief::getPayAmount)
                .reduce(ZERO, BigDecimal::add));
        return vo;
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
        LocalDateTime now = LocalDateTime.now();

        if (OrderStatus.PAID.name().equals(order.getStatus())) {
            // 商家未发货：买家可取消，先退款再释放库存/优惠券
            if (!Boolean.TRUE.equals(refund(orderNo, "用户取消订单退款").getSuccess())) {
                throw new BusinessException(OrderErrorCode.REFUND_FAILED);
            }
            order.refundBeforeShip(now);
            if (!orderRepository.markRefundedAfterCancel(orderNo, now)) {
                throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
            }
            releaseAfterCancel(order);
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
        notify(userId, "订单已取消", "订单 " + orderNo + " 已取消，库存与优惠券已释放");
    }

    /** 取消订单后释放库存与优惠券（按核销明细逐张释放，历史订单退回主券） */
    private void releaseAfterCancel(Order order) {
        List<OrderItem> items = orderRepository.findItems(order.getOrderNo());
        Results.data(productInternalApi.restoreStock(toStockRequest(items)));
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

    /** 调用支付服务模拟退款，未找到支付记录时视为无需退款 */
    private RefundResultVO refund(String orderNo, String reason) {
        RefundRequest request = new RefundRequest();
        request.setOrderNo(orderNo);
        request.setReason(reason);
        try {
            RefundResultVO result = Results.data(paymentInternalApi.refund(request));
            return result == null ? RefundResultVO.failure("退款服务未返回结果") : result;
        } catch (Exception ex) {
            log.error("调用支付服务退款失败: orderNo={}", orderNo, ex);
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
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long shopId, String orderNo, Long companyId) {
        Order order = requireOrder(orderNo);
        if (shopId != null && !shopId.equals(order.getShopId())) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
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
     * 积分商城兑换下单：生成免支付订单（积分全额抵扣），直接进入待发货。
     * 由 system 服务在扣积分成功后调用；扣库存失败抛异常，调用方回滚返还积分。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createRedeemOrder(OrderInternalDtos.RedeemOrderRequest request) {
        ProductBatchRequest batchRequest = new ProductBatchRequest();
        batchRequest.setIds(new ArrayList<>(List.of(request.getProductId())));
        List<ProductVO> products = Results.data(productInternalApi.batch(batchRequest));
        ProductVO product = products == null ? null : products.stream()
                .filter(p -> p.getId().equals(request.getProductId())).findFirst().orElse(null);
        if (product == null) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(OrderErrorCode.PRODUCT_OFF_SHELF, product.getName() + " 已下架");
        }
        if (product.getStock() == null || product.getStock() < 1) {
            throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, product.getName() + " 库存不足");
        }

        LocalDateTime now = LocalDateTime.now();
        String orderNo = orderNoGenerator.next();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setShopId(product.getShopId());
        order.setShopName(product.getShopName());
        order.setStatus(OrderStatus.PAID.name());
        order.setBizType(BIZ_TYPE_POINTS);
        order.setOriginalAmount(product.getPrice());
        order.setPromotionDiscount(ZERO);
        order.setCouponDiscount(ZERO);
        order.setFreightAmount(ZERO);
        order.setPayAmount(ZERO);
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setPayTime(now);
        orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setOrderNo(orderNo);
        item.setProductId(product.getId());
        item.setCategoryId(product.getCategoryId());
        item.setSkuId(0L);
        item.setSkuSpecs("");
        item.setProductName(product.getName());
        item.setProductEmoji(product.getEmoji());
        item.setProductColor(product.getColor());
        item.setShopId(product.getShopId());
        item.setShopName(product.getShopName());
        item.setPrice(product.getPrice());
        item.setQuantity(1);
        item.setSubtotal(product.getPrice());
        orderRepository.saveItems(List.of(item));

        StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
        stockItem.setProductId(product.getId());
        stockItem.setQuantity(1);
        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setItems(List.of(stockItem));
        try {
            Results.data(productInternalApi.deductStock(stockRequest));
        } catch (Exception ex) {
            log.error("积分兑换扣库存失败: productId={}", product.getId(), ex);
            throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, "库存扣减失败，请稍后重试");
        }
        notify(request.getUserId(), "积分兑换成功",
                "已用 " + (request.getPointsUsed() == null ? 0 : request.getPointsUsed())
                        + " 积分兑换「" + product.getName() + "」，订单 " + orderNo + " 待商家发货");
        return orderNo;
    }

    public OrderInternalDTO getInternal(String orderNo) {
        Order order = requireOrder(orderNo);
        OrderInternalDTO dto = new OrderInternalDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setStatus(order.getStatus());
        dto.setBizType(order.getBizType());
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

    private PreparedOrder prepare(Long userId, List<OrderItemRequest> itemRequests, List<Long> couponIds) {
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
            allItems.add(new PreparedItem(product, sku, skuId, quantity, subtotal));

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
        discountRequest.setItems(discountItems);
        DiscountResultVO discount = Results.data(discountInternalApi.calculate(discountRequest));

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

        return new PreparedOrder(allItems, shops, originalAmount, discount, stockItems);
    }

    /** 按店铺小计比例分摊优惠与费用承担，尾差给最后一个店铺 */
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

        List<AllocatedShop> result = new ArrayList<>();
        BigDecimal allocatedPay = ZERO;
        BigDecimal allocatedPromotion = ZERO;
        BigDecimal allocatedMerchantBear = ZERO;
        BigDecimal allocatedPlatformBear = ZERO;

        for (int i = 0; i < shops.size(); i++) {
            PreparedShop shop = shops.get(i);
            boolean last = i == shops.size() - 1;

            BigDecimal pay;
            BigDecimal promotion;
            BigDecimal merchantBear;
            BigDecimal platformBear;
            if (last) {
                pay = totalPay.subtract(allocatedPay).setScale(2, RoundingMode.HALF_UP);
                promotion = totalPromotion.subtract(allocatedPromotion).setScale(2, RoundingMode.HALF_UP);
                merchantBear = totalMerchantBear.subtract(allocatedMerchantBear).setScale(2, RoundingMode.HALF_UP);
                platformBear = totalPlatformBear.subtract(allocatedPlatformBear).setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal ratio = totalOriginal.signum() == 0 ? BigDecimal.ZERO
                        : shop.originalAmount.divide(totalOriginal, 6, RoundingMode.HALF_UP);
                pay = totalPay.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                promotion = totalPromotion.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                merchantBear = totalMerchantBear.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                platformBear = totalPlatformBear.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                allocatedPay = allocatedPay.add(pay);
                allocatedPromotion = allocatedPromotion.add(promotion);
                allocatedMerchantBear = allocatedMerchantBear.add(merchantBear);
                allocatedPlatformBear = allocatedPlatformBear.add(platformBear);
            }
            BigDecimal coupon = shop.originalAmount.subtract(pay).subtract(promotion);
            if (coupon.signum() < 0) {
                coupon = ZERO;
            }
            result.add(new AllocatedShop(shop.shopId, shop.shopName, shop.originalAmount,
                    promotion, coupon, merchantBear, platformBear, pay, shop.items));
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
        vo.setOriginalAmount(order.getOriginalAmount());
        vo.setPromotionDiscount(order.getPromotionDiscount());
        vo.setCouponDiscount(order.getCouponDiscount());
        vo.setMerchantBearAmount(order.getMerchantBearAmount());
        vo.setPlatformBearAmount(order.getPlatformBearAmount());
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
        return vo;
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

    private record PreparedItem(ProductVO product, ProductVO.SkuVO sku, Long skuId,
                                int quantity, BigDecimal subtotal) {
    }

    private record PreparedShop(Long shopId, String shopName, List<PreparedItem> items,
                                BigDecimal originalAmount) {
    }

    private record PreparedOrder(List<PreparedItem> allItems,
                                 List<PreparedShop> shops,
                                 BigDecimal originalAmount,
                                 DiscountResultVO discount,
                                 List<StockChangeRequest.StockItem> stockItems) {
    }

    private record AllocatedShop(Long shopId, String shopName, BigDecimal originalAmount,
                                 BigDecimal promotionDiscount, BigDecimal couponDiscount,
                                 BigDecimal merchantBearAmount, BigDecimal platformBearAmount,
                                 BigDecimal payAmount, List<PreparedItem> items) {
    }
}
