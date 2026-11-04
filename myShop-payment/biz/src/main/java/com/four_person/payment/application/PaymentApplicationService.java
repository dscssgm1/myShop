package com.four_person.payment.application;

import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.OrderInternalApi;
import com.four_person.order.api.dto.OrderGroupInternalDTO;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.payment.api.dto.CreatePaymentRequest;
import com.four_person.payment.api.dto.PaymentVO;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.payment.domain.model.Payment;
import com.four_person.payment.domain.repository.PaymentRepository;
import com.four_person.payment.enums.PaymentChannel;
import com.four_person.payment.enums.PaymentErrorCode;
import com.four_person.payment.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private static final String UNPAID = "UNPAID";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentRepository paymentRepository;
    private final OrderInternalApi orderInternalApi;

    public PaymentVO create(Long userId, CreatePaymentRequest request) {
        PaymentChannel channel = PaymentChannel.of(request.getChannel());
        boolean byGroup = StringUtils.hasText(request.getGroupNo());
        boolean byOrder = StringUtils.hasText(request.getOrderNo());
        if (byGroup == byOrder) {
            // 二选一：同时传/都不传均拒绝，避免歧义
            throw new BusinessException(PaymentErrorCode.PAY_PARAM_INVALID);
        }
        return byGroup ? createGroupPayment(userId, request.getGroupNo(), channel)
                : createOrderPayment(userId, request.getOrderNo(), channel);
    }

    /** 普通单订单支付单 */
    private PaymentVO createOrderPayment(Long userId, String orderNo, PaymentChannel channel) {
        OrderInternalDTO order = Results.data(orderInternalApi.getOrder(orderNo));
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        if (unpaidRecoveryBlocked(userId, order)) {
            throw new BusinessException(PaymentErrorCode.UNPAID_RECOVERY_EXISTS);
        }
        if (!UNPAID.equals(order.getStatus())) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_PAYABLE);
        }
        if (StringUtils.hasText(order.getGroupNo())) {
            // 合单子订单共享一张组支付单，单独支付会破坏「整组一次付清」语义
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_PAYABLE,
                    "该订单为跨店合单子订单，请在收银台整组支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount().signum() <= 0) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_PAYABLE, "订单金额异常");
        }

        Payment existing = paymentRepository.findByOrderNo(order.getOrderNo()).orElse(null);
        if (existing != null) {
            if (existing.isPending() || existing.isSuccess()) {
                return toVO(existing);
            }
            existing.reopen(order.getPayAmount(), channel.name());
            paymentRepository.save(existing);
            return toVO(existing);
        }

        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setAmount(order.getPayAmount());
        payment.setChannel(channel.name());
        payment.setStatus(PaymentStatus.PENDING.name());
        paymentRepository.save(payment);
        return toVO(payment);
    }

    /** 跨店合单支付单：一次付清整组，退款按子单分摊金额部分退款 */
    private PaymentVO createGroupPayment(Long userId, String groupNo, PaymentChannel channel) {
        OrderGroupInternalDTO group = Results.data(orderInternalApi.getOrderGroup(groupNo));
        if (group == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        if (!userId.equals(group.getUserId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        if (!UNPAID.equals(group.getStatus())) {
            throw new BusinessException(PaymentErrorCode.GROUP_NOT_PAYABLE);
        }
        if (group.getPayAmount() == null || group.getPayAmount().signum() <= 0) {
            throw new BusinessException(PaymentErrorCode.GROUP_NOT_PAYABLE, "订单组金额异常");
        }

        Payment existing = paymentRepository.findByGroupNo(groupNo).orElse(null);
        if (existing != null) {
            if (existing.isPending() || existing.isSuccess()) {
                return toVO(existing);
            }
            existing.reopen(group.getPayAmount(), channel.name());
            paymentRepository.save(existing);
            return toVO(existing);
        }

        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo());
        // order_no 记录组的核销单号（第一张子单），退款查询可按 groupNo 命中
        payment.setOrderNo(group.getCouponUseOrderNo());
        payment.setGroupNo(groupNo);
        payment.setUserId(userId);
        payment.setAmount(group.getPayAmount());
        payment.setChannel(channel.name());
        payment.setStatus(PaymentStatus.PENDING.name());
        paymentRepository.save(payment);
        return toVO(payment);
    }

    public PaymentVO get(Long userId, String paymentNo) {
        return toVO(requireOwned(userId, paymentNo));
    }

    public PaymentVO getByOrder(Long userId, String orderNo) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return toVO(payment);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentVO pay(Long userId, String paymentNo) {
        Payment payment = requireOwned(userId, paymentNo);
        if (payment.isSuccess()) {
            return toVO(payment);
        }
        LocalDateTime paidAt = LocalDateTime.now();
        String tradeNo = "SANDBOX" + payment.getPaymentNo();
        payment.markPaid(paidAt, tradeNo);
        if (!paymentRepository.markPaid(paymentNo, tradeNo, paidAt)) {
            Payment latest = requireOwned(userId, paymentNo);
            if (latest.isSuccess()) {
                return toVO(latest);
            }
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }

        PaidRequest paidRequest = new PaidRequest();
        paidRequest.setPaymentNo(paymentNo);
        paidRequest.setChannel(payment.getChannel());
        paidRequest.setPaidAt(paidAt);
        if (StringUtils.hasText(payment.getGroupNo())) {
            // 合单支付：整组子订单一次性置为已支付
            Results.data(orderInternalApi.groupPaid(payment.getGroupNo(), paidRequest));
        } else {
            Results.data(orderInternalApi.paid(payment.getOrderNo(), paidRequest));
        }
        return toVO(payment);
    }

    /** 订单服务调用：按订单/订单组退款，幂等；无支付记录时视为无需退款 */
    @Transactional(rollbackFor = Exception.class)
    public RefundResultVO refund(RefundRequest request) {
        RefundResultVO result = new RefundResultVO();
        result.setSuccess(true);
        result.setRefunded(false);

        Payment payment = StringUtils.hasText(request.getGroupNo())
                ? paymentRepository.findByGroupNo(request.getGroupNo()).orElse(null)
                : paymentRepository.findByOrderNo(request.getOrderNo()).orElse(null);
        if (payment == null) {
            result.setMessage("未找到支付记录，无需退款");
            return result;
        }
        result.setPaymentNo(payment.getPaymentNo());
        result.setAmount(payment.getAmount());

        // 本次退款额：合单子订单传分摊金额做部分退款，普通单整单全额退款
        BigDecimal delta = request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().compareTo(payment.getAmount()) >= 0
                ? payment.getAmount() : request.getAmount();
        boolean partial = delta.compareTo(payment.getAmount()) < 0;

        if (PaymentStatus.REFUNDED.name().equals(payment.getStatus())) {
            result.setRefunded(true);
            result.setStatus(payment.getStatus());
            result.setMessage("该支付单已退款");
            return result;
        }
        if (!payment.isSuccess()) {
            result.setStatus(payment.getStatus());
            result.setMessage("支付单未支付成功，无需退款");
            return result;
        }
        if (partial && !payment.hasRefundableBalance()) {
            result.setStatus(payment.getStatus());
            result.setMessage("该支付单可退余额不足");
            return result;
        }

        LocalDateTime refundedAt = LocalDateTime.now();
        String refundReason = request.getReason() == null || request.getReason().isBlank()
                ? "订单退款" : request.getReason();
        boolean updated = partial
                ? paymentRepository.markPartialRefund(payment.getPaymentNo(), delta, refundedAt, refundReason)
                : paymentRepository.markRefunded(payment.getPaymentNo(), refundedAt, refundReason);
        if (!updated) {
            Payment latest = paymentRepository.findByPaymentNo(payment.getPaymentNo()).orElse(payment);
            if (PaymentStatus.REFUNDED.name().equals(latest.getStatus())
                    || (partial && latest.isSuccess() && !latest.hasRefundableBalance())) {
                result.setRefunded(true);
                result.setStatus(latest.getStatus());
                result.setMessage("该支付单已退款");
                return result;
            }
            result.setSuccess(false);
            result.setMessage("退款状态更新失败，请重试");
            return result;
        }
        result.setRefunded(true);
        result.setAmount(delta);
        result.setStatus(partial ? PaymentStatus.SUCCESS.name() : PaymentStatus.REFUNDED.name());
        result.setMessage(partial ? "部分退款成功" : "退款成功");
        log.info("支付单 {} 已{}退款 {} 元，订单 {}，原因：{}",
                payment.getPaymentNo(), partial ? "部分" : "", delta, request.getOrderNo(), refundReason);
        return result;
    }

    /** 内部查询支付单状态，无支付记录时返回 null */
    public String paymentStatusByOrderNo(String orderNo) {
        return paymentRepository.findByOrderNo(orderNo)
                .map(Payment::getStatus)
                .orElse(null);
    }

    public void close(Long userId, String paymentNo) {
        Payment payment = requireOwned(userId, paymentNo);
        payment.close();
        if (PaymentStatus.CLOSED.name().equals(payment.getStatus())
                && !paymentRepository.markClosed(paymentNo)) {
            Payment latest = requireOwned(userId, paymentNo);
            if (!PaymentStatus.CLOSED.name().equals(latest.getStatus())) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
            }
        }
    }

    /** 补款订单本身可以支付，其他订单在补款订单未支付时禁止创建支付 */
    private boolean unpaidRecoveryBlocked(Long userId, OrderInternalDTO order) {
        if ("RECOVERY".equals(order.getBizType())) {
            return false;
        }
        return Boolean.TRUE.equals(Results.data(orderInternalApi.hasUnpaidRecovery(userId)));
    }

    private Payment requireOwned(Long userId, String paymentNo) {
        Payment payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        return payment;
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(FORMATTER)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private PaymentVO toVO(Payment payment) {
        PaymentVO vo = new PaymentVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderNo(payment.getOrderNo());
        vo.setGroupNo(payment.getGroupNo());
        vo.setAmount(payment.getAmount());
        vo.setRefundedAmount(payment.getRefundedAmount());
        vo.setChannel(payment.getChannel());
        vo.setChannelText(PaymentChannel.textOf(payment.getChannel()));
        vo.setStatus(payment.getStatus());
        vo.setStatusText(PaymentStatus.textOf(payment.getStatus()));
        vo.setTradeNo(payment.getTradeNo());
        vo.setPaidAt(payment.getPaidAt());
        vo.setRefundedAt(payment.getRefundedAt());
        vo.setRefundReason(payment.getRefundReason());
        vo.setCreatedAt(payment.getCreatedAt());
        return vo;
    }
}
