package com.four_person.order.application;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.logistics.api.LogisticsInternalApi;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.merchant.api.MerchantInternalApi;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.order.api.dto.AfterSaleCreateRequest;
import com.four_person.order.api.dto.AfterSaleReviewRequest;
import com.four_person.order.api.dto.AfterSaleVO;
import com.four_person.order.api.dto.OrderInternalDtos;
import com.four_person.order.api.dto.OrderItemVO;
import com.four_person.order.api.enums.AfterSaleStatus;
import com.four_person.order.api.enums.AfterSaleType;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.AfterSale;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.repository.AfterSaleRepository;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.order.infrastructure.support.OrderNoGenerator;
import com.four_person.payment.api.PaymentInternalApi;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.product.api.ProductInternalApi;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后应用服务。
 *
 * <p>流程：用户/客服创建售后 → 商家审核（24 小时未处理自动转平台）→ 商家或平台同意 →
 * 商家触发退款 → 商家收到退货后不认可可向平台申诉 → 平台支持申诉时生成补款订单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleApplicationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final int MERCHANT_REVIEW_HOURS = 24;
    private static final int APPOINTMENT_DAYS = 2;

    private final AfterSaleRepository afterSaleRepository;
    private final OrderRepository orderRepository;
    private final PaymentInternalApi paymentInternalApi;
    private final LogisticsInternalApi logisticsInternalApi;
    private final MerchantInternalApi merchantInternalApi;
    private final ProductInternalApi productInternalApi;
    private final MessageInternalApi messageInternalApi;
    private final MemberInternalApi memberInternalApi;
    private final CommissionApplicationService commissionApplicationService;
    private final OrderGroupService orderGroupService;
    private final OrderNoGenerator orderNoGenerator;

    // ==================== 创建 / 查询 ====================

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO create(Long actorId, String group, Long actorShopId, AfterSaleCreateRequest request) {
        AfterSaleType type = parseType(request.getType());
        Long targetUserId = actorId;
        String initiatorRole = "USER";
        Long initiatorId = null;

        if (request.getUserId() != null && !request.getUserId().equals(actorId)) {
            Order order = requireOrder(request.getOrderNo());
            if ("PLATFORM".equals(group)) {
                // 平台客服代用户发起
            } else if ("MERCHANT".equals(group) && actorShopId != null
                    && actorShopId.equals(order.getShopId())) {
                // 商家客服代本店订单用户发起
            } else {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN, "只能为自己发起售后");
            }
            targetUserId = request.getUserId();
            initiatorRole = "CS";
            initiatorId = actorId;
        }

        Order order = requireOrder(request.getOrderNo());
        if (!order.getUserId().equals(targetUserId)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!OrderStatus.SHIPPED.name().equals(order.getStatus())
                && !OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单发货后才能申请售后");
        }

        AfterSale latest = afterSaleRepository.findLatestByOrderNo(order.getOrderNo()).orElse(null);
        if (latest != null && !AfterSaleStatus.isFinal(latest.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        AfterSale afterSale = new AfterSale();
        afterSale.setAfterSaleNo(orderNoGenerator.nextAfterSale());
        afterSale.setOrderNo(order.getOrderNo());
        afterSale.setUserId(targetUserId);
        afterSale.setShopId(order.getShopId());
        afterSale.setShopName(order.getShopName());
        afterSale.setType(type.name());
        afterSale.setReason(request.getReason());
        afterSale.setDescription(request.getDescription() == null ? "" : request.getDescription());
        afterSale.setAmount(order.getPayAmount());
        afterSale.setStatus(AfterSaleStatus.PENDING_MERCHANT.name());
        afterSale.setOriginalOrderStatus(order.getStatus());
        afterSale.setInitiatorRole(initiatorRole);
        afterSale.setInitiatorId(initiatorId);
        afterSale.setMerchantReason("");
        afterSale.setPlatformReason("");
        afterSale.setReturnTrackingNo("");
        afterSale.setPickupCode("");
        afterSale.setRecoveryOrderNo("");
        afterSale.setDeadlineAt(now.plusHours(MERCHANT_REVIEW_HOURS));

        order.requestAfterSale();
        if (!orderRepository.markAfterSale(order.getOrderNo())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        afterSaleRepository.save(afterSale);

        notify(targetUserId, "售后申请已提交",
                "订单 " + order.getOrderNo() + " 的「" + type.getText() + "」申请已提交，商家将在 24 小时内处理");
        return detailInternal(afterSale, true);
    }

    public PageResult<AfterSaleVO> page(Long userId, String status, int page, int size) {
        return assemblePage(afterSaleRepository.pageByUser(userId, status, page, size), false);
    }

    public PageResult<AfterSaleVO> merchantPage(Long shopId, String status, int page, int size) {
        return assemblePage(afterSaleRepository.pageByShop(shopId, status, page, size), false);
    }

    public PageResult<AfterSaleVO> platformPage(String status, int page, int size) {
        return assemblePage(afterSaleRepository.pageAll(status, page, size), false);
    }

    public AfterSaleVO detail(Long actorId, String group, Long actorShopId, String afterSaleNo) {
        AfterSale afterSale = require(afterSaleNo);
        checkReadAccess(afterSale, actorId, group, actorShopId);
        return detailInternal(afterSale, true);
    }

    // ==================== 商家审核 ====================

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO merchantReview(Long shopId, String afterSaleNo, AfterSaleReviewRequest request) {
        AfterSale afterSale = require(afterSaleNo);
        if (shopId == null || !shopId.equals(afterSale.getShopId())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!AfterSaleStatus.PENDING_MERCHANT.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(request.getApproved())) {
            String reason = StringUtils.hasText(request.getReason()) ? request.getReason() : "商家同意售后";
            if (!afterSaleRepository.merchantApprove(afterSale.getId(), reason, now)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
            }
            afterSale.setStatus(AfterSaleStatus.APPROVED.name());
            afterSale.setMerchantReason(reason);
            afterSale.setReviewedAt(now);
            // 防御：正常路径订单已处于售后中，若此前被恢复过则重新进入售后中（0 行表示无需变更）
            orderRepository.markAfterSale(afterSale.getOrderNo());
            createReturnShipmentIfNeeded(afterSale, reason);
            notify(afterSale.getUserId(), "商家已同意售后",
                    AfterSaleType.RETURN_REFUND.name().equals(afterSale.getType())
                            ? "退货申请已通过，请按取件码预约快递上门取件，商家收到退货后将为您退款"
                            : "仅退款申请已通过，商家将尽快为您退款");
        } else {
            String reason = request.getReason();
            if (!StringUtils.hasText(reason)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "请填写拒绝原因");
            }
            if (!afterSaleRepository.merchantReject(afterSale.getId(), reason, now)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
            }
            restoreOrder(afterSale);
            notify(afterSale.getUserId(), "售后申请被商家拒绝",
                    "拒绝原因：" + reason + "。如不认可，可申请平台介入处理");
        }
        return detailInternal(require(afterSaleNo), true);
    }

    /** 用户对商家拒绝结果申请平台介入 */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO escalate(Long userId, String afterSaleNo) {
        AfterSale afterSale = require(afterSaleNo);
        if (!afterSale.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!AfterSaleStatus.MERCHANT_REJECTED.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID);
        }
        if (!afterSaleRepository.escalate(afterSale.getId())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
        }
        notify(userId, "售后已提交平台审核", "您的售后申请已转交平台审核，请耐心等待");
        return detailInternal(require(afterSaleNo), true);
    }

    /**
     * 前端倒计时结束后触发的超时升级。以服务端时间为准校验截止时间确实已过，
     * 防止客户端时钟不准导致提前升级；定时任务仍作为兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO escalateTimeout(Long actorId, String group, Long actorShopId, String afterSaleNo) {
        AfterSale afterSale = require(afterSaleNo);
        boolean owner = afterSale.getUserId().equals(actorId);
        boolean shopOwner = "MERCHANT".equals(group) && actorShopId != null
                && actorShopId.equals(afterSale.getShopId());
        if (!owner && !shopOwner && !"PLATFORM".equals(group)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!AfterSaleStatus.PENDING_MERCHANT.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后不在商家审核阶段，无需超时升级");
        }
        LocalDateTime now = LocalDateTime.now();
        if (afterSale.getDeadlineAt() == null || afterSale.getDeadlineAt().isAfter(now)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "商家审核尚未超时");
        }
        // 条件 SQL 里再次校验截止时间，并发场景下也不会提前升级
        if (!afterSaleRepository.autoEscalate(afterSale.getId())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
        }
        notify(afterSale.getUserId(), "售后已转平台审核",
                "商家超过 24 小时未处理您的售后申请，系统已自动转交平台审核");
        log.info("售后单 {} 商家审核超时，已转平台审核（触发方：{}）",
                afterSaleNo, owner ? "用户" : shopOwner ? "商家" : "平台");
        return detailInternal(require(afterSaleNo), true);
    }

    // ==================== 平台审核 ====================

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO platformReview(String afterSaleNo, AfterSaleReviewRequest request) {
        AfterSale afterSale = require(afterSaleNo);
        if (!AfterSaleStatus.PENDING_PLATFORM.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(request.getApproved())) {
            String reason = StringUtils.hasText(request.getReason()) ? request.getReason() : "平台支持用户售后申请";
            if (!afterSaleRepository.platformApprove(afterSale.getId(), reason, now)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
            }
            afterSale.setStatus(AfterSaleStatus.APPROVED.name());
            afterSale.setPlatformReason(reason);
            afterSale.setReviewedAt(now);
            // 商家拒绝时订单已被恢复原状态，平台通过后需重新进入售后中，否则后续退款会被状态条件拦下
            orderRepository.markAfterSale(afterSale.getOrderNo());
            createReturnShipmentIfNeeded(afterSale, reason);
            notify(afterSale.getUserId(), "平台已支持您的售后申请",
                    "平台审核已通过，商家将尽快为您办理退款");
        } else {
            String reason = request.getReason();
            if (!StringUtils.hasText(reason)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "请填写拒绝原因");
            }
            if (!afterSaleRepository.platformReject(afterSale.getId(), reason, now)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
            }
            restoreOrder(afterSale);
            notify(afterSale.getUserId(), "平台未支持您的售后申请", "平台审核意见：" + reason);
        }
        return detailInternal(require(afterSaleNo), true);
    }

    // ==================== 退款 / 申诉 ====================

    /** 商家触发退款（平台也可代为操作） */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO refund(Long actorShopId, String afterSaleNo) {
        AfterSale afterSale = require(afterSaleNo);
        if (actorShopId != null && !actorShopId.equals(afterSale.getShopId())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!AfterSaleStatus.APPROVED.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后尚未通过审核，暂不能退款");
        }

        RefundResultVO result = refundOrder(afterSale.getOrderNo(), "售后退款：" + afterSale.getAfterSaleNo());
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            throw new BusinessException(OrderErrorCode.REFUND_FAILED, result.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        if (!afterSaleRepository.markRefunded(afterSale.getId(), now)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
        }
        if (!orderRepository.markRefundedAfterSale(afterSale.getOrderNo(), now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        // 合单黄金不变量：组内最后一个子订单退款关闭后，整组关闭并全额退回优惠券
        orderRepository.findByOrderNo(afterSale.getOrderNo())
                .ifPresent(order -> {
                    orderGroupService.releaseCouponsIfGroupFullyClosed(order);
                    refundDeductedPoints(order, afterSale);
                });
        // 退货退款：商家确认收到退货并退款，商品回到库存
        if (AfterSaleType.RETURN_REFUND.name().equals(afterSale.getType())) {
            List<OrderItem> items = orderRepository.findItems(afterSale.getOrderNo());
            try {
                Results.data(productInternalApi.restoreStock(toStockRequest(items)));
            } catch (Exception ex) {
                log.error("售后退款恢复库存失败，需要人工处理: orderNo={}", afterSale.getOrderNo(), ex);
            }
        }
        deductEarnedPoints(afterSale);
        reverseCommission(afterSale.getOrderNo());
        notify(afterSale.getUserId(), "售后退款成功",
                "售后单 " + afterSale.getAfterSaleNo() + " 已退款，金额 ¥" + afterSale.getAmount());
        return detailInternal(require(afterSaleNo), true);
    }

    /** 商家退款后收到退货不满足退款要求，向平台申诉 */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO appeal(Long shopId, String afterSaleNo, String reason) {
        AfterSale afterSale = require(afterSaleNo);
        if (shopId == null || !shopId.equals(afterSale.getShopId())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!AfterSaleType.RETURN_REFUND.name().equals(afterSale.getType())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "仅退货退款支持申诉");
        }
        if (!AfterSaleStatus.REFUNDED.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "仅已退款的售后单可以申诉");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "请填写申诉理由");
        }
        if (!afterSaleRepository.markAppealing(afterSale.getId(), reason)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
        }
        notify(afterSale.getUserId(), "商家已发起售后申诉",
                "商家对已退款的售后单提出申诉，平台将复核，请留意后续通知");
        return detailInternal(require(afterSaleNo), true);
    }

    /** 平台处理商家申诉：成立则生成补款订单 */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO appealReview(String afterSaleNo, AfterSaleReviewRequest request) {
        AfterSale afterSale = require(afterSaleNo);
        if (!AfterSaleStatus.APPEALING.name().equals(afterSale.getStatus())) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID);
        }
        String opinion = StringUtils.hasText(request.getReason()) ? request.getReason() : "";
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(request.getApproved())) {
            String recoveryOrderNo = orderNoGenerator.next();
            String platformReason = "申诉成立。商家申诉理由：" + afterSale.getPlatformReason()
                    + (StringUtils.hasText(opinion) ? "；平台意见：" + opinion : "")
                    + "；已生成补款订单 " + recoveryOrderNo;
            if (!afterSaleRepository.appealApprove(afterSale.getId(), platformReason,
                    recoveryOrderNo, now)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
            }
            createRecoveryOrder(afterSale, recoveryOrderNo);
            notify(afterSale.getUserId(), "商家申诉成立，请支付补款订单",
                    "平台复核后认为退货不满足退款条件，已生成补款订单 " + recoveryOrderNo
                            + "，请完成支付后商家将把货物重新寄回。支付前其他购买操作将受限");
        } else {
            String platformReason = "申诉不成立"
                    + (StringUtils.hasText(opinion) ? "：" + opinion : "");
            if (!afterSaleRepository.appealReject(afterSale.getId(), platformReason, now)) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
            }
            notify(afterSale.getUserId(), "商家申诉未成立", "平台复核后维持原退款结果");
        }
        return detailInternal(require(afterSaleNo), true);
    }

    // ==================== 预约取件 / 超时 ====================

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO appointment(Long userId, String afterSaleNo, LocalDateTime appointmentTime) {
        AfterSale afterSale = require(afterSaleNo);
        if (!afterSale.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
        if (!AfterSaleType.RETURN_REFUND.name().equals(afterSale.getType())) {
            throw new BusinessException(OrderErrorCode.APPOINTMENT_INVALID, "仅退货退款支持预约取件");
        }
        // 商家可在退货揽收前触发退款，因此已退款/申诉中的售后仍允许预约取件
        String status = afterSale.getStatus();
        boolean approved = AfterSaleStatus.APPROVED.name().equals(status)
                || AfterSaleStatus.REFUNDED.name().equals(status)
                || AfterSaleStatus.APPEALING.name().equals(status)
                || AfterSaleStatus.APPEAL_APPROVED.name().equals(status);
        if (!approved) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后尚未通过审核");
        }
        if (!StringUtils.hasText(afterSale.getReturnTrackingNo())) {
            throw new BusinessException(OrderErrorCode.RETURN_SHIPMENT_FAILED, "退货运单尚未生成");
        }
        LocalDateTime now = LocalDateTime.now();
        if (appointmentTime == null || appointmentTime.isBefore(now.minusMinutes(5))
                || appointmentTime.isAfter(now.plusDays(APPOINTMENT_DAYS))) {
            throw new BusinessException(OrderErrorCode.APPOINTMENT_INVALID);
        }

        LogisticsDtos.ShipmentAppointmentRequest request = new LogisticsDtos.ShipmentAppointmentRequest();
        request.setAppointmentTime(appointmentTime);
        try {
            Results.data(logisticsInternalApi.appoint(afterSale.getReturnTrackingNo(), request));
        } catch (Exception ex) {
            log.error("预约上门取件失败: afterSaleNo={}", afterSaleNo, ex);
            throw new BusinessException(OrderErrorCode.APPOINTMENT_INVALID, "预约失败，请稍后重试");
        }
        if (!afterSaleRepository.setAppointment(afterSale.getId(), appointmentTime)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "售后状态已变化，请刷新后重试");
        }
        notify(userId, "取件预约成功",
                "已预约快递员上门取件，请保持电话畅通，取件码 " + afterSale.getPickupCode());
        return detailInternal(require(afterSaleNo), true);
    }

    /** 物流回调：退货运单已被商家签收，通知买家等待商家确认退款 */
    public void onReturnSigned(OrderInternalDtos.ReturnSignedRequest request) {
        AfterSale afterSale = afterSaleRepository.findByReturnTrackingNo(request.getTrackingNo()).orElse(null);
        if (afterSale == null) {
            log.warn("退货运单签收回调未找到售后单: trackingNo={}", request.getTrackingNo());
            return;
        }
        notify(afterSale.getUserId(), "退货已签收",
                "售后单 " + afterSale.getAfterSaleNo() + " 的退货商品已送达商家，商家确认后将为您办理退款");
    }

    /** 商家超过 24 小时未审核，自动转平台审核 */
    public void autoEscalateTimeouts() {
        List<AfterSale> timeouts = afterSaleRepository.findTimeout(LocalDateTime.now());
        for (AfterSale afterSale : timeouts) {
            try {
                if (afterSaleRepository.autoEscalate(afterSale.getId())) {
                    notify(afterSale.getUserId(), "售后已转平台审核",
                            "商家超过 24 小时未处理您的售后申请，系统已自动转交平台审核");
                    log.info("售后单 {} 商家审核超时，已自动转平台", afterSale.getAfterSaleNo());
                }
            } catch (Exception ex) {
                log.warn("售后自动转平台失败: afterSaleNo={}", afterSale.getAfterSaleNo(), ex);
            }
        }
    }

    // ==================== 内部逻辑 ====================

    private void createReturnShipmentIfNeeded(AfterSale afterSale, String reason) {
        if (!AfterSaleType.RETURN_REFUND.name().equals(afterSale.getType())) {
            return;
        }
        Order order = requireOrder(afterSale.getOrderNo());
        MerchantDtos.ShopVO shop;
        try {
            shop = Results.data(merchantInternalApi.shop(order.getShopId()));
        } catch (Exception ex) {
            log.error("读取商家退货地址失败: shopId={}", order.getShopId(), ex);
            throw new BusinessException(OrderErrorCode.RETURN_SHIPMENT_FAILED, "读取商家退货地址失败，请稍后重试");
        }
        if (shop == null) {
            throw new BusinessException(OrderErrorCode.RETURN_SHIPMENT_FAILED, "商家店铺信息不存在");
        }

        List<OrderItem> items = orderRepository.findItems(order.getOrderNo());
        String returnAddress = (StringUtils.hasText(shop.getRegion()) ? shop.getRegion() : "")
                + (shop.getAddress() == null ? "" : shop.getAddress());
        String pickupCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));

        LogisticsDtos.ReturnShipmentRequest request = new LogisticsDtos.ReturnShipmentRequest();
        request.setAfterSaleNo(afterSale.getAfterSaleNo());
        request.setOrderNo(order.getOrderNo());
        request.setShopId(order.getShopId());
        request.setShopName(order.getShopName());
        request.setPickupAddress(order.getReceiverAddress());
        request.setReceiverName(StringUtils.hasText(shop.getContact()) ? shop.getContact() : shop.getName());
        request.setReceiverPhone(shop.getPhone());
        request.setReceiverAddress(returnAddress);
        request.setReceiverRegion(shop.getRegion());
        request.setItemsText(items.stream()
                .map(item -> item.getProductName() + " ×" + item.getQuantity())
                .reduce((a, b) -> a + "、" + b).orElse(""));
        request.setPickupCode(pickupCode);

        LogisticsDtos.ShipmentVO shipment;
        try {
            shipment = Results.data(logisticsInternalApi.createReturnShipment(request));
        } catch (Exception ex) {
            log.error("创建售后退货运单失败: afterSaleNo={}", afterSale.getAfterSaleNo(), ex);
            throw new BusinessException(OrderErrorCode.RETURN_SHIPMENT_FAILED);
        }
        if (shipment == null || !StringUtils.hasText(shipment.getTrackingNo())) {
            throw new BusinessException(OrderErrorCode.RETURN_SHIPMENT_FAILED);
        }
        if (!afterSaleRepository.setReturnShipment(afterSale.getId(), shipment.getTrackingNo(), pickupCode)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID);
        }
        afterSale.setReturnTrackingNo(shipment.getTrackingNo());
        afterSale.setPickupCode(pickupCode);
        log.info("售后退货运单已创建: afterSaleNo={}, trackingNo={}, reason={}",
                afterSale.getAfterSaleNo(), shipment.getTrackingNo(), reason);
    }

    /** 平台支持商家申诉：生成补款订单，用户支付后商家重新发货 */
    private void createRecoveryOrder(AfterSale afterSale, String recoveryOrderNo) {
        Order original = requireOrder(afterSale.getOrderNo());
        List<OrderItem> originalItems = orderRepository.findItems(original.getOrderNo());
        // 补款订单按原订单实付金额生成，避免用户实际支付与补款金额不一致
        BigDecimal amount = original.getPayAmount() == null
                ? ZERO : original.getPayAmount().setScale(2, RoundingMode.HALF_UP);

        Order order = new Order();
        order.setOrderNo(recoveryOrderNo);
        order.setUserId(original.getUserId());
        order.setShopId(original.getShopId());
        order.setShopName(original.getShopName());
        order.setStatus(OrderStatus.UNPAID.name());
        order.setBizType("RECOVERY");
        order.setSourceOrderNo(original.getOrderNo());
        order.setAfterSaleId(afterSale.getId());
        order.setOriginalAmount(amount);
        order.setPromotionDiscount(ZERO);
        order.setCouponDiscount(ZERO);
        order.setFreightAmount(ZERO);
        order.setPayAmount(amount);
        order.setReceiverName(original.getReceiverName());
        order.setReceiverPhone(original.getReceiverPhone());
        order.setReceiverAddress(original.getReceiverAddress());
        order.setRemark("售后申诉成立补款订单（原订单 " + original.getOrderNo() + "）");
        orderRepository.save(order);

        List<OrderItem> items = originalItems.stream().map(source -> {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setProductId(source.getProductId());
            item.setCategoryId(source.getCategoryId());
            item.setSkuId(source.getSkuId());
            item.setSkuSpecs(source.getSkuSpecs());
            item.setProductName(source.getProductName());
            item.setProductEmoji(source.getProductEmoji());
            item.setProductColor(source.getProductColor());
            item.setShopId(source.getShopId());
            item.setShopName(source.getShopName());
            item.setPrice(source.getPrice());
            item.setQuantity(source.getQuantity());
            item.setSubtotal(source.getSubtotal());
            return item;
        }).toList();
        orderRepository.saveItems(items);

        // 补款订单对应的货物已退回商家，重新销售需要重新占用库存
        try {
            Results.data(productInternalApi.deductStock(toStockRequest(items)));
        } catch (Exception ex) {
            log.warn("补款订单扣减库存失败，允许继续: orderNo={}, cause={}", recoveryOrderNo, ex.getMessage());
        }
    }

    private void restoreOrder(AfterSale afterSale) {
        if (!orderRepository.restoreStatus(afterSale.getOrderNo(), afterSale.getOriginalOrderStatus())) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
    }

    /** 售后退款成功后退回该订单下单时抵现扣减的积分，按订单号幂等；失败不影响退款 */
    private void refundDeductedPoints(Order order, AfterSale afterSale) {
        if (order.getPointsUsed() == null || order.getPointsUsed() <= 0) {
            return;
        }
        try {
            MemberDtos.RefundSpendRequest request = new MemberDtos.RefundSpendRequest();
            request.setUserId(order.getUserId());
            request.setBizNo(order.getOrderNo());
            request.setRemark("售后退款退回抵现积分");
            Integer refunded = Results.data(memberInternalApi.refundSpend(request));
            if (refunded != null && refunded > 0) {
                notify(order.getUserId(), "积分退回",
                        "售后单 " + afterSale.getAfterSaleNo() + " 退款成功，退回抵现使用的 " + refunded + " 积分");
            }
        } catch (Exception ex) {
            log.warn("售后退款退回抵现积分失败: afterSaleNo={}, orderNo={}",
                    afterSale.getAfterSaleNo(), order.getOrderNo(), ex);
        }
    }

    /** 售后退款成功后按单扣回订单完成时发放的积分，失败不影响退款 */
    private void deductEarnedPoints(AfterSale afterSale) {
        MemberDtos.EarnPointsRequest request = new MemberDtos.EarnPointsRequest();
        request.setUserId(afterSale.getUserId());
        request.setBizNo(afterSale.getOrderNo());
        request.setRemark("售后退款扣回：" + afterSale.getAfterSaleNo());
        try {
            Integer deducted = Results.data(memberInternalApi.refundEarn(request));
            if (deducted != null && deducted > 0) {
                notify(afterSale.getUserId(), "积分扣回",
                        "售后单 " + afterSale.getAfterSaleNo() + " 退款成功，扣回该订单发放的 " + deducted + " 积分");
            }
        } catch (Exception ex) {
            log.warn("售后退款扣回积分失败: afterSaleNo={}, orderNo={}",
                    afterSale.getAfterSaleNo(), afterSale.getOrderNo(), ex);
        }
    }

    /** 售后退款成功冲销已计佣金（生成负数流水，与计佣流水相抵），失败不影响退款 */
    private void reverseCommission(String orderNo) {
        try {
            commissionApplicationService.reverse(orderNo);
        } catch (Exception ex) {
            log.warn("售后退款冲销佣金失败: orderNo={}", orderNo, ex);
        }
    }

    private RefundResultVO refundOrder(String orderNo, String reason) {
        RefundRequest request = new RefundRequest();
        request.setOrderNo(orderNo);
        // 合单子订单共用一张组支付单：按子单分摊后的实付金额部分退款
        orderRepository.findByOrderNo(orderNo).ifPresent(order -> {
            if (StringUtils.hasText(order.getGroupNo())) {
                request.setGroupNo(order.getGroupNo());
                request.setAmount(order.getPayAmount());
            }
        });
        request.setReason(reason);
        try {
            RefundResultVO result = Results.data(paymentInternalApi.refund(request));
            return result == null ? RefundResultVO.failure("退款服务未返回结果") : result;
        } catch (Exception ex) {
            log.error("调用支付服务退款失败: orderNo={}", orderNo, ex);
            return RefundResultVO.failure("退款服务暂不可用，请稍后重试");
        }
    }

    private PageResult<AfterSaleVO> assemblePage(PageResult<AfterSale> result, boolean withShipment) {
        Map<String, List<OrderItem>> itemMap = orderRepository.findItemsByOrderNos(
                result.getList().stream().map(AfterSale::getOrderNo).distinct().toList());
        List<AfterSaleVO> records = result.getList().stream()
                .map(afterSale -> toVO(afterSale,
                        itemMap.getOrDefault(afterSale.getOrderNo(), List.of()), withShipment))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
    }

    private AfterSaleVO detailInternal(AfterSale afterSale, boolean withShipment) {
        return toVO(afterSale, orderRepository.findItems(afterSale.getOrderNo()), withShipment);
    }

    private AfterSaleVO toVO(AfterSale afterSale, List<OrderItem> items, boolean withShipment) {
        AfterSaleVO vo = new AfterSaleVO();
        vo.setAfterSaleNo(afterSale.getAfterSaleNo());
        vo.setOrderNo(afterSale.getOrderNo());
        vo.setUserId(afterSale.getUserId());
        vo.setShopId(afterSale.getShopId());
        vo.setShopName(afterSale.getShopName());
        vo.setType(afterSale.getType());
        vo.setTypeText(AfterSaleType.textOf(afterSale.getType()));
        vo.setReason(afterSale.getReason());
        vo.setDescription(afterSale.getDescription());
        vo.setAmount(afterSale.getAmount());
        vo.setStatus(afterSale.getStatus());
        vo.setStatusText(AfterSaleStatus.textOf(afterSale.getStatus()));
        vo.setOriginalOrderStatus(afterSale.getOriginalOrderStatus());
        vo.setInitiatorRole(afterSale.getInitiatorRole());
        vo.setInitiatorText("CS".equals(afterSale.getInitiatorRole()) ? "客服代发起" : "用户发起");
        vo.setMerchantReason(afterSale.getMerchantReason());
        vo.setPlatformReason(afterSale.getPlatformReason());
        vo.setReturnTrackingNo(afterSale.getReturnTrackingNo());
        vo.setPickupCode(afterSale.getPickupCode());
        vo.setRecoveryOrderNo(afterSale.getRecoveryOrderNo());
        vo.setAppointmentTime(afterSale.getAppointmentTime());
        vo.setDeadlineAt(afterSale.getDeadlineAt());
        vo.setReviewedAt(afterSale.getReviewedAt());
        vo.setRefundedAt(afterSale.getRefundedAt());
        vo.setCreatedAt(afterSale.getCreatedAt());
        vo.setItems(items.stream().map(this::toItemVO).toList());

        if (withShipment && StringUtils.hasText(afterSale.getReturnTrackingNo())) {
            try {
                LogisticsDtos.ShipmentVO shipment = Results.data(
                        logisticsInternalApi.shipmentByTrackingNo(afterSale.getReturnTrackingNo()));
                if (shipment != null) {
                    vo.setReturnShipmentStatus(shipment.getStatus());
                    vo.setReturnShipmentStatusText(shipment.getStatusText());
                    vo.setReturnShipmentCompany(shipment.getCompanyName());
                }
            } catch (Exception ex) {
                log.warn("读取退货运单失败: trackingNo={}, cause={}",
                        afterSale.getReturnTrackingNo(), ex.getMessage());
            }
        }
        return vo;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setSkuId(item.getSkuId());
        vo.setSkuSpecs(item.getSkuSpecs());
        vo.setProductName(item.getProductName());
        vo.setProductEmoji(item.getProductEmoji());
        vo.setProductColor(item.getProductColor());
        vo.setShopId(item.getShopId());
        vo.setShopName(item.getShopName());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
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

    private void checkReadAccess(AfterSale afterSale, Long actorId, String group, Long actorShopId) {
        if ("PLATFORM".equals(group)) {
            return;
        }
        if ("MERCHANT".equals(group)) {
            if (actorShopId == null || !actorShopId.equals(afterSale.getShopId())) {
                throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
            }
            return;
        }
        if (!afterSale.getUserId().equals(actorId)) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_FORBIDDEN);
        }
    }

    private AfterSaleType parseType(String type) {
        try {
            return AfterSaleType.valueOf(type);
        } catch (Exception ex) {
            throw new BusinessException(OrderErrorCode.AFTER_SALE_STATUS_INVALID, "不支持的售后类型");
        }
    }

    private AfterSale require(String afterSaleNo) {
        return afterSaleRepository.findByNo(afterSaleNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.AFTER_SALE_NOT_FOUND));
    }

    private Order requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

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
}
