package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.RuleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 优惠规则边界：门槛相等/差一分、每满阶梯取整方向、封顶、舍入归零。
 */
class DiscountRuleTest {

    // ==================== 构造校验 ====================

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new DiscountRule(null, Money.zero(), 0, Money.of("1.00"), null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectFullReductionWithoutAmount() {
        assertThatThrownBy(() -> DiscountRule.fullReduction(Money.of("100.00"), 0, Money.zero()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.INVALID_RULE.getCode());
    }

    @Test
    void shouldRejectNegativePerEvery() {
        assertThatThrownBy(() -> new DiscountRule(RuleType.FULL_REDUCTION, Money.zero(), -1, Money.of("1.00"), null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectInvalidDiscountRate() {
        assertThatThrownBy(() -> DiscountRule.discount(BigDecimal.ZERO, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DiscountRule.discount(BigDecimal.ONE, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DiscountRule.discount(new BigDecimal("1.10"), null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DiscountRule.discount(null, null))
                .isInstanceOf(BusinessException.class);
        // 0.99 合法（贴近上界）
        assertThat(DiscountRule.discount(new BigDecimal("0.99"), null).discountFor(Money.of("100.00")).amount())
                .isEqualByComparingTo("1.00");
    }

    // ==================== 满减门槛 ====================

    @Test
    void shouldApplyWhenEligibleEqualsThreshold() {
        DiscountRule rule = DiscountRule.fullReduction(Money.of("100.00"), 0, Money.of("20.00"));
        assertThat(rule.discountFor(Money.of("100.00")).amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldNotApplyWhenEligibleBelowThresholdByOneCent() {
        DiscountRule rule = DiscountRule.fullReduction(Money.of("100.00"), 0, Money.of("20.00"));
        assertThat(rule.discountFor(Money.of("99.99")).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldTreatNullOrZeroEligibleAsNoDiscount() {
        DiscountRule rule = DiscountRule.fullReduction(Money.of("100.00"), 0, Money.of("20.00"));
        assertThat(rule.discountFor(null).amount()).isEqualByComparingTo("0.00");
        assertThat(rule.discountFor(Money.zero()).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCapFullReductionAtEligible() {
        DiscountRule rule = DiscountRule.fullReduction(Money.of("50.00"), 0, Money.of("200.00"));
        assertThat(rule.discountFor(Money.of("100.00")).amount()).isEqualByComparingTo("100.00");
    }

    // ==================== 每满阶梯 ====================

    @Test
    void shouldRoundLadderTimesDown() {
        DiscountRule rule = DiscountRule.fullReduction(Money.zero(), 100, Money.of("50.00"));
        // 199 → 只算 1 层
        assertThat(rule.discountFor(Money.of("199.00")).amount()).isEqualByComparingTo("50.00");
        // 200 → 2 层
        assertThat(rule.discountFor(Money.of("200.00")).amount()).isEqualByComparingTo("100.00");
        // 299 → 仍 2 层
        assertThat(rule.discountFor(Money.of("299.00")).amount()).isEqualByComparingTo("100.00");
        // 不足一层
        assertThat(rule.discountFor(Money.of("99.99")).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCapLadderDiscountAtEligibleWhenAmountExceedsPerEvery() {
        // 每满 100 减 150：两层 = 300 > 小计 200 → 封顶为小计
        DiscountRule rule = DiscountRule.fullReduction(Money.zero(), 100, Money.of("150.00"));
        assertThat(rule.discountFor(Money.of("200.00")).amount()).isEqualByComparingTo("200.00");
        assertThat(rule.discountFor(Money.of("100.00")).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldPreferLadderOverThresholdWhenBothConfigured() {
        // perEvery > 0 时门槛不参与判断（实现即如此，此处固化行为）
        DiscountRule rule = new DiscountRule(RuleType.FULL_REDUCTION, Money.of("500.00"), 100,
                Money.of("10.00"), null, null);
        assertThat(rule.discountFor(Money.of("200.00")).amount()).isEqualByComparingTo("20.00");
    }

    // ==================== 折扣规则 ====================

    @Test
    void shouldApplyDiscountRate() {
        DiscountRule rule = DiscountRule.discount(new BigDecimal("0.80"), null);
        assertThat(rule.discountFor(Money.of("100.00")).amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldRespectDiscountThreshold() {
        DiscountRule rule = new DiscountRule(RuleType.DISCOUNT, Money.of("200.00"), 0,
                Money.zero(), new BigDecimal("0.50"), null);
        assertThat(rule.discountFor(Money.of("199.99")).amount()).isEqualByComparingTo("0.00");
        assertThat(rule.discountFor(Money.of("200.00")).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldCapAtMaxDiscountOnlyWhenPositive() {
        DiscountRule capped = DiscountRule.discount(new BigDecimal("0.50"), Money.of("30.00"));
        assertThat(capped.discountFor(Money.of("100.00")).amount()).isEqualByComparingTo("30.00");
        assertThat(capped.discountFor(Money.of("50.00")).amount()).isEqualByComparingTo("25.00");

        // maxDiscount 为 0/null 视为不封顶
        DiscountRule uncapped = DiscountRule.discount(new BigDecimal("0.50"), Money.zero());
        assertThat(uncapped.discountFor(Money.of("1000.00")).amount()).isEqualByComparingTo("500.00");
        assertThat(DiscountRule.discount(new BigDecimal("0.50"), null)
                .discountFor(Money.of("1000.00")).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldRoundDiscountHalfUp() {
        // 10.05 × 0.5 = 5.025 → HALF_UP 5.03
        DiscountRule rule = DiscountRule.discount(new BigDecimal("0.50"), null);
        assertThat(rule.discountFor(Money.of("10.05")).amount()).isEqualByComparingTo("5.03");
    }

    @Test
    void shouldRoundTinyDiscountToZero() {
        // 0.01 × 0.1 = 0.001 → 舍入为 0.00，优惠归零且不超小计
        DiscountRule rule = DiscountRule.discount(new BigDecimal("0.90"), null);
        assertThat(rule.discountFor(Money.of("0.01")).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldNeverDiscountMoreThanEligible() {
        // rate 是支付比例：0.01 = 0.1 折 → 1.00 元只优惠 0.99，恰好不超小计
        DiscountRule rule = DiscountRule.discount(new BigDecimal("0.01"), null);
        assertThat(rule.discountFor(Money.of("1.00")).amount()).isEqualByComparingTo("0.99");
    }
}
