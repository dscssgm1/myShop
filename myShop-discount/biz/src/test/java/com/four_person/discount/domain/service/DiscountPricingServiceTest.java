package com.four_person.discount.domain.service;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.domain.model.CouponOffer;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.DiscountRule;
import com.four_person.discount.domain.model.DiscountSelection;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.Scope;
import com.four_person.discount.domain.model.UserCoupon;
import com.four_person.discount.enums.CouponStatus;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.RuleType;
import com.four_person.discount.enums.ScopeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountPricingServiceTest {

    private final DiscountPricingService service = new DiscountPricingService();
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void shouldApplyFullReductionPerEvery() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("300.00", 300, "50.00"), Scope.all(), true)),
                List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("150.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("850.00");
        assertThat(result.promotionId()).isEqualTo(1L);
    }

    @Test
    void shouldApplyThresholdFullReduction() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "250.00", 1)),
                List.of(promotion(1L, fullReduction("200.00", 0, "30.00"), Scope.all(), true)),
                List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("30.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("220.00");
    }

    @Test
    void shouldCapPercentageDiscount() {
        DiscountRule rule = new DiscountRule(RuleType.DISCOUNT, Money.zero(), 0,
                Money.zero(), new BigDecimal("0.50"), Money.of("300.00"));
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, rule, Scope.all(), true)),
                List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("300.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("700.00");
    }

    @Test
    void shouldOnlyApplyToMatchingCategory() {
        Scope scope = new Scope(ScopeType.CATEGORY, Set.of(1L));
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "500.00", 1), line(2L, 4L, "500.00", 1)),
                List.of(promotion(1L, fullReduction("400.00", 0, "100.00"), scope, true)),
                List.of(), null, now);

        assertThat(result.originalAmount().amount()).isEqualByComparingTo("1000.00");
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("900.00");
    }

    @Test
    void shouldStackOnePromotionAndOneCouponWhenAllowed() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), true)),
                List.of(offer(10L, "300.00", "50.00")), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("50.00");
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("150.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("850.00");
        assertThat(result.couponId()).isEqualTo(10L);
    }

    @Test
    void shouldNotStackCouponWhenPromotionDisallows() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), false)),
                List.of(offer(10L, "300.00", "40.00")), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("900.00");
        assertThat(result.couponId()).isNull();
    }

    @Test
    void shouldChooseCouponAloneWhenBetter() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), false)),
                List.of(offer(10L, "300.00", "150.00")), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("150.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("850.00");
        assertThat(result.promotionId()).isNull();
    }

    @Test
    void shouldIgnoreCouponBelowThreshold() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "100.00", 1)),
                List.of(), List.of(offer(10L, "300.00", "40.00")), null, now);

        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectUnavailableSelectedCoupon() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, "100.00", 1)), List.of(), List.of(), 99L, now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    void shouldNeverDiscountBelowZero() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("50.00", 0, "80.00"), Scope.all(), true)),
                List.of(offer(10L, "0.00", "50.00")), null, now);

        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("100.00");
    }

    private OrderLine line(Long productId, Long categoryId, String price, int quantity) {
        return new OrderLine(productId, categoryId, Money.of(price), quantity);
    }

    private DiscountRule fullReduction(String threshold, int perEvery, String amount) {
        return new DiscountRule(RuleType.FULL_REDUCTION, Money.of(threshold), perEvery,
                Money.of(amount), null, null);
    }

    private Promotion promotion(Long id, DiscountRule rule, Scope scope, boolean stackable) {
        return new Promotion(id, "活动" + id, "平台大促", rule, scope, stackable,
                now.minusDays(1), now.plusDays(1), Promotion.STATUS_ACTIVE, "", "");
    }

    private CouponOffer offer(Long couponId, String threshold, String amount) {
        CouponTemplate template = new CouponTemplate(1L, "优惠券", Money.of(threshold),
                Money.of(amount), Scope.all(), 100, 0, 2, now.minusDays(1), now.plusDays(30),
                CouponTemplate.STATUS_ACTIVE);
        UserCoupon coupon = new UserCoupon(couponId, 1L, 1L, CouponStatus.UNUSED, null,
                now.minusDays(1), null, now.plusDays(30));
        return new CouponOffer(coupon, template);
    }
}
