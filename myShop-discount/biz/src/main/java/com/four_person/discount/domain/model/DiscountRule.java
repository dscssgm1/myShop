package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.RuleType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优惠规则值对象：满减（含每满）与折扣（可封顶）。
 */
public record DiscountRule(RuleType type,
                           Money thresholdAmount,
                           int perEvery,
                           Money discountAmount,
                           BigDecimal discountRate,
                           Money maxDiscount) {

    public DiscountRule {
        if (type == null) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "优惠规则类型不能为空");
        }
        thresholdAmount = thresholdAmount == null ? Money.zero() : thresholdAmount;
        discountAmount = discountAmount == null ? Money.zero() : discountAmount;
        if (perEvery < 0) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "每满金额不能为负数");
        }
        if (type == RuleType.FULL_REDUCTION && !discountAmount.isPositive()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "满减活动必须设置优惠金额");
        }
        if (type == RuleType.DISCOUNT
                && (discountRate == null || discountRate.signum() <= 0 || discountRate.compareTo(BigDecimal.ONE) >= 0)) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "折扣率必须在 0 到 1 之间");
        }
    }

    public static DiscountRule fullReduction(Money threshold, int perEvery, Money amount) {
        return new DiscountRule(RuleType.FULL_REDUCTION, threshold, perEvery, amount, null, null);
    }

    public static DiscountRule discount(BigDecimal rate, Money maxDiscount) {
        return new DiscountRule(RuleType.DISCOUNT, Money.zero(), 0, Money.zero(), rate, maxDiscount);
    }

    public Money discountFor(Money eligible) {
        if (eligible == null || !eligible.isPositive()) {
            return Money.zero();
        }
        if (type == RuleType.FULL_REDUCTION) {
            if (perEvery > 0) {
                int times = eligible.amount()
                        .divide(BigDecimal.valueOf(perEvery), 0, RoundingMode.DOWN)
                        .intValue();
                if (times <= 0) {
                    return Money.zero();
                }
                return discountAmount.multiply(BigDecimal.valueOf(times)).min(eligible);
            }
            if (eligible.compareTo(thresholdAmount) < 0) {
                return Money.zero();
            }
            return discountAmount.min(eligible);
        }
        if (eligible.compareTo(thresholdAmount) < 0) {
            return Money.zero();
        }
        Money discount = eligible.multiply(BigDecimal.ONE.subtract(discountRate));
        if (maxDiscount != null && maxDiscount.isPositive()) {
            discount = discount.min(maxDiscount);
        }
        return discount.min(eligible);
    }
}
