package com.four_person.payment.domain.repository;

import com.four_person.payment.domain.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByOrderNo(String orderNo);

    /** 合单支付单按订单组号查询 */
    Optional<Payment> findByGroupNo(String groupNo);

    Payment save(Payment payment);

    /** 条件更新：待支付 -> 支付成功 */
    boolean markPaid(String paymentNo, String tradeNo, LocalDateTime paidAt);

    boolean markClosed(String paymentNo);

    /** 条件更新：支付成功 -> 已退款 */
    boolean markRefunded(String paymentNo, LocalDateTime refundedAt, String reason);

    /** 合单部分退款：累计已退金额 delta，退满时支付单整体置为已退款 */
    boolean markPartialRefund(String paymentNo, BigDecimal delta, LocalDateTime refundedAt, String reason);
}
