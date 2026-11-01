package com.four_person.payment.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void shouldMarkPendingPaymentAsPaid() {
        Payment payment = payment(PaymentStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();

        payment.markPaid(now, "TRADE-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS.name());
        assertThat(payment.getPaidAt()).isEqualTo(now);
        assertThat(payment.getTradeNo()).isEqualTo("TRADE-1");
        assertThatThrownBy(() -> payment.markPaid(now, "TRADE-2"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldClosePendingPayment() {
        Payment payment = payment(PaymentStatus.PENDING);
        payment.close();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CLOSED.name());
    }

    @Test
    void shouldRejectClosingSuccessfulPayment() {
        Payment payment = payment(PaymentStatus.SUCCESS);

        assertThatThrownBy(payment::close).isInstanceOf(BusinessException.class);
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setPaymentNo("PAY-TEST");
        payment.setStatus(status.name());
        return payment;
    }
}
