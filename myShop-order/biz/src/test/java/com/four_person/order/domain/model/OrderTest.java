package com.four_person.order.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void shouldWalkThroughHappyPath() {
        Order order = order(OrderStatus.UNPAID);

        order.pay(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID.name());
        assertThat(order.getPayTime()).isEqualTo(now);

        order.ship(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED.name());

        order.complete(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
    }

    @Test
    void shouldCancelOnlyUnpaidOrder() {
        Order order = order(OrderStatus.UNPAID);
        order.cancel(now);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThatThrownBy(() -> order.cancel(now)).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectPayingCancelledOrder() {
        Order order = order(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> order.pay(now)).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectShippingUnpaidOrder() {
        Order order = order(OrderStatus.UNPAID);

        assertThatThrownBy(() -> order.ship(now)).isInstanceOf(BusinessException.class);
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setOrderNo("MS-TEST");
        order.setStatus(status.name());
        return order;
    }
}
