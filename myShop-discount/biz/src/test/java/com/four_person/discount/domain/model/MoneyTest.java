package com.four_person.discount.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金额值对象边界：两位小数、HALF_UP 舍入、加减乘与比较。
 */
class MoneyTest {

    @Test
    void shouldDefaultNullToZero() {
        assertThat(new Money(null).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldNormalizeScaleToTwoDecimals() {
        assertThat(Money.of("10").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10.5").amount()).isEqualByComparingTo("10.50");
        assertThat(Money.of("10.555").amount()).isEqualByComparingTo("10.56");
    }

    @Test
    void shouldRoundHalfUp() {
        assertThat(Money.of("1.005").amount()).isEqualByComparingTo("1.01");
        assertThat(Money.of("1.004").amount()).isEqualByComparingTo("1.00");
        assertThat(Money.of("2.675").amount()).isEqualByComparingTo("2.68");
        assertThat(Money.of("0.005").amount()).isEqualByComparingTo("0.01");
    }

    @Test
    void shouldAddAndSubtract() {
        Money sum = Money.of("10.01").add(Money.of("20.02"));
        assertThat(sum.amount()).isEqualByComparingTo("30.03");

        Money diff = Money.of("10.00").subtract(Money.of("30.00"));
        // Money 本身不夹负数，负值由上层（payAmount）兜底
        assertThat(diff.amount()).isEqualByComparingTo("-20.00");
    }

    @Test
    void shouldMultiplyWithHalfUpRounding() {
        assertThat(Money.of("10.05").multiply(new BigDecimal("0.5")).amount()).isEqualByComparingTo("5.03");
        assertThat(Money.of("33.33").multiply(new BigDecimal("0.33")).amount()).isEqualByComparingTo("11.00");
        assertThat(Money.of("99.99").multiply(new BigDecimal("3")).amount()).isEqualByComparingTo("299.97");
    }

    @Test
    void shouldPickMinMax() {
        Money small = Money.of("1.00");
        Money large = Money.of("2.00");
        assertThat(small.min(large)).isEqualTo(small);
        assertThat(large.min(small)).isEqualTo(small);
        assertThat(small.max(large)).isEqualTo(large);
        assertThat(small.max(small)).isEqualTo(small);
    }

    @Test
    void shouldJudgeSign() {
        assertThat(Money.of("0.00").isPositive()).isFalse();
        assertThat(Money.of("-0.01").isPositive()).isFalse();
        assertThat(Money.of("0.01").isPositive()).isTrue();
        assertThat(Money.of("0.00").isZero()).isTrue();
        assertThat(Money.of("0.01").isZero()).isFalse();
    }

    @Test
    void shouldCompareEqualAcrossScales() {
        assertThat(Money.of("1.0")).isEqualTo(Money.of("1.00"));
        assertThat(Money.of("1.01").compareTo(Money.of("1.00"))).isPositive();
        assertThat(Money.zero().add(Money.of("1.00"))).isEqualTo(Money.of("1.00"));
    }
}
