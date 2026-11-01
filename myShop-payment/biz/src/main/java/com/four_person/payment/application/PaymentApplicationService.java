package com.four_person.payment.application;

import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.OrderInternalApi;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.payment.api.dto.CreatePaymentRequest;
import com.four_person.payment.api.dto.PaymentVO;
import com.four_person.payment.domain.model.Payment;
import com.four_person.payment.domain.repository.PaymentRepository;
import com.four_person.payment.enums.PaymentChannel;
import com.four_person.payment.enums.PaymentErrorCode;
import com.four_person.payment.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        OrderInternalDTO order = Results.data(orderInternalApi.getOrder(request.getOrderNo()));
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FORBIDDEN);
        }
        if (!UNPAID.equals(order.getStatus())) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_PAYABLE);
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
        Results.data(orderInternalApi.paid(payment.getOrderNo(), paidRequest));
        return toVO(payment);
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
        vo.setAmount(payment.getAmount());
        vo.setChannel(payment.getChannel());
        vo.setChannelText(PaymentChannel.textOf(payment.getChannel()));
        vo.setStatus(payment.getStatus());
        vo.setStatusText(PaymentStatus.textOf(payment.getStatus()));
        vo.setTradeNo(payment.getTradeNo());
        vo.setPaidAt(payment.getPaidAt());
        vo.setCreatedAt(payment.getCreatedAt());
        return vo;
    }
}
