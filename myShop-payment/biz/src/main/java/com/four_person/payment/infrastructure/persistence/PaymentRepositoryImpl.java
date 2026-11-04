package com.four_person.payment.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.payment.domain.model.Payment;
import com.four_person.payment.domain.repository.PaymentRepository;
import com.four_person.payment.infrastructure.persistence.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentMapper paymentMapper;

    @Override
    public Optional<Payment> findByPaymentNo(String paymentNo) {
        return Optional.ofNullable(paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getPaymentNo, paymentNo)));
    }

    @Override
    public Optional<Payment> findByOrderNo(String orderNo) {
        return Optional.ofNullable(paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderNo, orderNo)));
    }

    @Override
    public Optional<Payment> findByGroupNo(String groupNo) {
        return Optional.ofNullable(paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getGroupNo, groupNo)));
    }

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            paymentMapper.insert(payment);
        } else {
            paymentMapper.updateById(payment);
        }
        return payment;
    }

    @Override
    public boolean markPaid(String paymentNo, String tradeNo, LocalDateTime paidAt) {
        return paymentMapper.markPaid(paymentNo, tradeNo, paidAt) > 0;
    }

    @Override
    public boolean markClosed(String paymentNo) {
        return paymentMapper.markClosed(paymentNo) > 0;
    }

    @Override
    public boolean markRefunded(String paymentNo, LocalDateTime refundedAt, String reason) {
        return paymentMapper.markRefunded(paymentNo, refundedAt, reason) > 0;
    }

    @Override
    public boolean markPartialRefund(String paymentNo, BigDecimal delta, LocalDateTime refundedAt, String reason) {
        return paymentMapper.markPartialRefund(paymentNo, delta, refundedAt, reason) > 0;
    }
}
