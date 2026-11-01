package com.four_person.payment.domain.repository;

import com.four_person.payment.domain.model.Payment;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByOrderNo(String orderNo);

    Payment save(Payment payment);

    /** 条件更新：待支付 -> 支付成功 */
    boolean markPaid(String paymentNo, String tradeNo, LocalDateTime paidAt);

    boolean markClosed(String paymentNo);
}
