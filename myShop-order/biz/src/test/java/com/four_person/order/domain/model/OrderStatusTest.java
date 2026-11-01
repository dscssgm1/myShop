package com.four_person.order.domain.model;

import com.four_person.order.api.enums.OrderStatus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void shouldReturnChineseText() {
        assertThat(OrderStatus.textOf("UNPAID")).isEqualTo("待付款");
        assertThat(OrderStatus.textOf("PAID")).isEqualTo("待发货");
        assertThat(OrderStatus.textOf("SHIPPED")).isEqualTo("待收货");
        assertThat(OrderStatus.textOf("COMPLETED")).isEqualTo("已完成");
        assertThat(OrderStatus.textOf("CANCELLED")).isEqualTo("已取消");
    }

    @Test
    void shouldRecognizePaidStatuses() {
        assertThat(OrderStatus.isPaid("PAID")).isTrue();
        assertThat(OrderStatus.isPaid("SHIPPED")).isTrue();
        assertThat(OrderStatus.isPaid("COMPLETED")).isTrue();
        assertThat(OrderStatus.isPaid("UNPAID")).isFalse();
        assertThat(OrderStatus.isPaid("CANCELLED")).isFalse();
    }
}
