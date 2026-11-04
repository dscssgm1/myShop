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

    // ==================== 活动规则 ====================

    @Test
    void shouldApplyFullReductionPerEvery() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("300.00", 300, "50.00"), Scope.all(), true, null)),
                List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("150.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("850.00");
        assertThat(result.primaryPromotion()).map(Promotion::getId).contains(1L);
    }

    @Test
    void shouldApplyThresholdFullReduction() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "250.00", 1)),
                List.of(promotion(1L, fullReduction("200.00", 0, "30.00"), Scope.all(), true, null)),
                List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("30.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("220.00");
    }

    @Test
    void shouldCapPercentageDiscount() {
        DiscountRule rule = new DiscountRule(RuleType.DISCOUNT, Money.zero(), 0,
                Money.zero(), new BigDecimal("0.50"), Money.of("300.00"));
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, rule, Scope.all(), true, null)),
                List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("300.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("700.00");
    }

    @Test
    void shouldOnlyApplyToMatchingCategory() {
        Scope scope = new Scope(ScopeType.CATEGORY, Set.of(1L));
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "500.00", 1), line(2L, 4L, 2L, "500.00", 1)),
                List.of(promotion(1L, fullReduction("400.00", 0, "100.00"), scope, true, null)),
                List.of(), null, now);

        assertThat(result.originalAmount().amount()).isEqualByComparingTo("1000.00");
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("900.00");
    }

    // ==================== 平台活动 × 商家活动 ====================

    @Test
    void shouldPickBestMerchantPromotionPerShop() {
        Promotion shopOne = merchantPromotion(11L, 100L,
                fullReduction("0.00", 0, "60.00"), Scope.shop());
        Promotion shopOneBetter = merchantPromotion(12L, 100L,
                fullReduction("0.00", 0, "80.00"), Scope.shop());
        Promotion shopTwo = merchantPromotion(13L, 200L,
                fullReduction("0.00", 0, "40.00"), Scope.shop());

        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1), line(2L, 2L, 200L, "300.00", 1)),
                List.of(shopOne, shopOneBetter, shopTwo),
                List.of(), null, now);

        // 店铺 100 取更优的 80，店铺 200 取 40，互不冲突可并存
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("120.00");
        assertThat(result.promotions()).extracting(Promotion::getId)
                .containsExactlyInAnyOrder(12L, 13L);
    }

    @Test
    void shouldResolveConflictBetweenPlatformAndMerchantByBiggerDiscount() {
        Promotion platform = promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null);
        Promotion merchant = merchantPromotion(2L, 100L,
                fullReduction("0.00", 0, "150.00"), Scope.shop());

        // 同一店铺的商品同时命中两个活动 → 冲突，保留商家活动（150 > 100）
        DiscountSelection merchantWins = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1)),
                List.of(platform, merchant), List.of(), null, now);
        assertThat(merchantWins.promotions()).extracting(Promotion::getId).containsExactly(2L);
        assertThat(merchantWins.promotionDiscount().amount()).isEqualByComparingTo("150.00");

        // 商家活动优惠更小时保留平台活动
        Promotion smallMerchant = merchantPromotion(3L, 100L,
                fullReduction("0.00", 0, "30.00"), Scope.shop());
        DiscountSelection platformWins = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1)),
                List.of(platform, smallMerchant), List.of(), null, now);
        assertThat(platformWins.promotions()).extracting(Promotion::getId).containsExactly(1L);
    }

    @Test
    void shouldMatchShopScopeOnlyForOwnedLines() {
        Promotion shopOnePromo = merchantPromotion(11L, 100L,
                fullReduction("0.00", 0, "50.00"), Scope.shop());

        // 店铺 200 的商品不受店铺 100 活动影响
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 200L, "500.00", 1)),
                List.of(shopOnePromo), List.of(), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.promotions()).isEmpty();
    }

    // ==================== 优惠券叠加 ====================

    @Test
    void shouldStackPromotionAndCouponWhenAllowed() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), true, null)),
                List.of(offer(10L, 1L, "300.00", "50.00")), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("50.00");
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("150.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("850.00");
        assertThat(result.primaryCoupon()).map(offer -> offer.coupon().getId()).contains(10L);
        assertThat(result.promotionCouponStackable()).isTrue();
    }

    @Test
    void shouldStackMultipleCouponsUpToLimit() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "30.00"),
                        offer(11L, 1L, "0.00", "20.00"),
                        offer(12L, 1L, "0.00", "10.00"),
                        offer(13L, 1L, "0.00", "5.00")),
                null, now);

        // 上限 3 张：30 + 20 + 10 = 60，第 4 张被舍弃
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("60.00");
        assertThat(result.coupons()).hasSize(3);
        assertThat(result.payAmount().amount()).isEqualByComparingTo("940.00");
    }

    @Test
    void shouldChooseCouponAloneWhenPromotionDisallowsStacking() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), false, null)),
                List.of(offer(10L, 1L, "300.00", "150.00")), null, now);

        // 活动不让叠券，券更优 → 只用券
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("150.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("850.00");
        assertThat(result.promotions()).isEmpty();
    }

    @Test
    void shouldKeepPromotionWhenBetterThanCoupon() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), false, null)),
                List.of(offer(10L, 1L, "300.00", "40.00")), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("900.00");
        assertThat(result.primaryCoupon()).isEmpty();
        assertThat(result.promotionCouponStackable()).isFalse();
    }

    @Test
    void shouldIgnoreCouponBelowThreshold() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(), List.of(offer(10L, 1L, "300.00", "40.00")), null, now);

        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRespectUserSelectedCoupons() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "30.00"), offer(11L, 1L, "0.00", "20.00")),
                List.of(11L), now);

        // 只用用户指定的 11 号券，不自动补选
        assertThat(result.coupons()).extracting(offer -> offer.coupon().getId()).containsExactly(11L);
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldRejectUnavailableSelectedCoupon() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)), List.of(), List.of(), List.of(99L), now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    void shouldRejectSelectedCouponBelowThreshold() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "300.00", "40.00")),
                List.of(10L), now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    void shouldNeverDiscountBelowZero() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("50.00", 0, "80.00"), Scope.all(), true, null)),
                List.of(offer(10L, 1L, "0.00", "50.00")), null, now);

        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("100.00");
    }

    // ==================== 费用承担分摊 ====================

    @Test
    void shouldSplitBearByPlatformRateAndCouponOwnership() {
        // 平台活动 100 优惠、平台承担 50% → 平台 50 / 商家 50；平台券 40 全平台承担
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("500.00", 0, "100.00"), Scope.all(), true,
                        new BigDecimal("50"))),
                List.of(offer(10L, 1L, "0.00", "40.00")), null, now);

        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("140.00");
        assertThat(result.platformBearAmount().amount()).isEqualByComparingTo("90.00");
        assertThat(result.merchantBearAmount().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void merchantActivityAndMerchantCouponNeverChargedToPlatform() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 100L, "1000.00", 1)),
                List.of(merchantPromotion(11L, 100L, fullReduction("0.00", 0, "100.00"), Scope.shop())),
                List.of(merchantOffer(10L, 100L, "0.00", "40.00")), null, now);

        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("140.00");
        assertThat(result.platformBearAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.merchantBearAmount().amount()).isEqualByComparingTo("140.00");
    }

    // ==================== 边界补充：购物车与规则 ====================

    @Test
    void emptyCartReturnsAllZeroEvenWithOffers() {
        DiscountSelection result = service.calculate(List.of(),
                List.of(promotion(1L, fullReduction("0.00", 0, "50.00"), Scope.all(), true, null)),
                List.of(offer(10L, 1L, "0.00", "50.00")), null, now);

        assertThat(result.originalAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.promotions()).isEmpty();
        assertThat(result.coupons()).isEmpty();
    }

    @Test
    void thresholdBoundaryExactlyEqualApplies() {
        DiscountSelection atThreshold = service.calculate(
                List.of(line(1L, 1L, 1L, "200.00", 1)),
                List.of(promotion(1L, fullReduction("200.00", 0, "30.00"), Scope.all(), true, null)),
                List.of(), null, now);
        assertThat(atThreshold.promotionDiscount().amount()).isEqualByComparingTo("30.00");

        DiscountSelection oneCentBelow = service.calculate(
                List.of(line(1L, 1L, 1L, "199.99", 1)),
                List.of(promotion(1L, fullReduction("200.00", 0, "30.00"), Scope.all(), true, null)),
                List.of(), null, now);
        assertThat(oneCentBelow.promotionDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(oneCentBelow.promotions()).isEmpty();
    }

    @Test
    void fullReductionBiggerThanSubtotalZeroesOutPay() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("50.00", 0, "200.00"), Scope.all(), true, null)),
                List.of(offer(10L, 1L, "0.00", "50.00")), null, now);

        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
        // 预算被活动吃光：50 元的券只能抵 0，整单被舍弃
        assertThat(result.coupons()).isEmpty();
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void perEveryLadderBoundaries() {
        Promotion promo = promotion(1L, fullReduction("0.00", 100, "50.00"), Scope.all(), true, null);
        assertThat(service.calculate(List.of(line(1L, 1L, 1L, "199.00", 1)),
                List.of(promo), List.of(), null, now).promotionDiscount().amount())
                .isEqualByComparingTo("50.00");
        assertThat(service.calculate(List.of(line(1L, 1L, 1L, "200.00", 1)),
                List.of(promo), List.of(), null, now).promotionDiscount().amount())
                .isEqualByComparingTo("100.00");
        assertThat(service.calculate(List.of(line(1L, 1L, 1L, "299.99", 1)),
                List.of(promo), List.of(), null, now).promotionDiscount().amount())
                .isEqualByComparingTo("100.00");
    }

    // ==================== 边界补充：活动选择 ====================

    @Test
    void twoPlatformPromotionsPickBestAndTieBreaksBySmallerId() {
        Promotion bigger = promotion(2L, fullReduction("0.00", 0, "120.00"), Scope.all(), true, null);
        Promotion best = promotion(1L, fullReduction("0.00", 0, "150.00"), Scope.all(), true, null);
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)), List.of(bigger, best), List.of(), null, now);
        assertThat(result.promotions()).extracting(Promotion::getId).containsExactly(1L);

        // 优惠相同时取 id 更小者，保证结果稳定
        Promotion tieA = promotion(5L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null);
        Promotion tieB = promotion(4L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null);
        DiscountSelection tie = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)), List.of(tieA, tieB), List.of(), null, now);
        assertThat(tie.promotions()).extracting(Promotion::getId).containsExactly(4L);
    }

    @Test
    void inactivePromotionsNeverApplied() {
        Promotion endedStatus = new Promotion(1L, 0L, "已结束", null, fullReduction("0.00", 0, "100.00"),
                Scope.all(), true, false, null, now.minusDays(2), now.plusDays(2),
                Promotion.STATUS_ENDED, "", "");
        Promotion notStarted = new Promotion(2L, 0L, "未开始", null, fullReduction("0.00", 0, "100.00"),
                Scope.all(), true, false, null, now.plusSeconds(1), now.plusDays(2),
                Promotion.STATUS_ACTIVE, "", "");
        Promotion expired = new Promotion(3L, 0L, "已过期", null, fullReduction("0.00", 0, "100.00"),
                Scope.all(), true, false, null, now.minusDays(2), now.minusSeconds(1),
                Promotion.STATUS_ACTIVE, "", "");

        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(endedStatus, notStarted, expired), List.of(), null, now);
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.promotions()).isEmpty();
    }

    @Test
    void conflictAgainstAllMerchantShopsComparesAggregatedMerchantTotal() {
        // 平台全活动与商家活动命中行相交 → 冲突整体裁决：商家两侧合计 90 < 平台 100 → 只留平台
        Promotion platform = promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null);
        Promotion shopA = merchantPromotion(11L, 100L, fullReduction("0.00", 0, "60.00"), Scope.shop());
        Promotion shopB = merchantPromotion(12L, 200L, fullReduction("0.00", 0, "30.00"), Scope.shop());
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1), line(2L, 2L, 200L, "500.00", 1)),
                List.of(platform, shopA, shopB), List.of(), null, now);
        assertThat(result.promotions()).extracting(Promotion::getId).containsExactly(1L);
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void conflictWithEqualDiscountKeepsPlatform() {
        Promotion platform = promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null);
        Promotion merchant = merchantPromotion(2L, 100L, fullReduction("0.00", 0, "100.00"), Scope.shop());
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1)),
                List.of(platform, merchant), List.of(), null, now);
        assertThat(result.promotions()).extracting(Promotion::getId).containsExactly(1L);
    }

    @Test
    void disjointPlatformAndMerchantPromotionsCoexist() {
        // 平台活动只圈 B 店商品，商家活动只圈 A 店商品，命中行不相交 → 并存
        Promotion platform = promotion(1L, fullReduction("0.00", 0, "20.00"),
                new Scope(ScopeType.PRODUCT, Set.of(2L)), true, null);
        Promotion shopA = merchantPromotion(11L, 100L, fullReduction("0.00", 0, "30.00"), Scope.shop());
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1), line(2L, 2L, 200L, "500.00", 1)),
                List.of(platform, shopA), List.of(), null, now);
        assertThat(result.promotions()).extracting(Promotion::getId).containsExactlyInAnyOrder(1L, 11L);
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("50.00");
        assertThat(result.promotionHits()).hasSameSizeAs(result.promotions());
    }

    // ==================== 边界补充：优惠券预算与范围 ====================

    @Test
    void couponTruncatedByRemainingBudgetAfterPromotion() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "60.00"), Scope.all(), true, null)),
                List.of(offer(10L, 1L, "0.00", "50.00")), null, now);

        // 券面 50 > 预算 40 → 截断为 40，合计优惠恰为商品总额
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("40.00");
        assertThat(result.couponHits().get(0).amount()).isEqualByComparingTo("40.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void thirdCouponDroppedWhenBudgetExhausted() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "80.00"),
                        offer(11L, 1L, "0.00", "50.00"),
                        offer(12L, 1L, "0.00", "40.00")), null, now);

        // 80 + 20（截断）后预算归零，第三张被舍弃
        assertThat(result.coupons()).extracting(offer -> offer.coupon().getId())
                .containsExactly(10L, 11L);
        assertThat(result.couponHits().get(1).amount()).isEqualByComparingTo("20.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void userSelectedDuplicateIdsCountOnce() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "30.00")),
                List.of(10L, 10L, 10L), now);
        assertThat(result.coupons()).hasSize(1);
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void userSelectedOverLimitThrowsWithMessage() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "30.00"), offer(11L, 1L, "0.00", "30.00"),
                        offer(12L, 1L, "0.00", "30.00"), offer(13L, 1L, "0.00", "30.00")),
                List.of(10L, 11L, 12L, 13L), now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单笔订单最多叠加 3 张优惠券");
    }

    @Test
    void selectedCouponOutOfScopeRejected() {
        // 券只适用分类 7，购物车全是分类 8 → 自动选券跳过；用户指定则明确报错
        CouponTemplate scopedTemplate = new CouponTemplate(1L, 0L, "分类券", Money.of("0.00"),
                Money.of("30.00"), new Scope(ScopeType.CATEGORY, Set.of(7L)), 100, 0, 2,
                null, null, now.minusDays(1), now.plusDays(30), CouponTemplate.STATUS_ACTIVE);
        CouponOffer scoped = new CouponOffer(
                new UserCoupon(10L, 1L, 1L, CouponStatus.UNUSED, null, now.minusDays(1), null, now.plusDays(30)),
                scopedTemplate);

        DiscountSelection auto = service.calculate(
                List.of(line(1L, 8L, 1L, "1000.00", 1)), List.of(), List.of(scoped), null, now);
        assertThat(auto.coupons()).isEmpty();

        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 8L, 1L, "1000.00", 1)), List.of(), List.of(scoped), List.of(10L), now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.COUPON_NOT_AVAILABLE.getCode());
    }

    @Test
    void expiredCouponSkippedInAutoAndRejectedWhenSelected() {
        CouponTemplate template = new CouponTemplate(1L, 0L, "过期券", Money.of("0.00"),
                Money.of("30.00"), Scope.all(), 100, 0, 2, null, null,
                now.minusDays(1), now.plusDays(30), CouponTemplate.STATUS_ACTIVE);
        CouponOffer expiredOffer = new CouponOffer(
                new UserCoupon(10L, 1L, 1L, CouponStatus.UNUSED, null, now.minusDays(1), null, now.minusSeconds(1)),
                template);

        DiscountSelection auto = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)), List.of(), List.of(expiredOffer), null, now);
        assertThat(auto.coupons()).isEmpty();

        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)), List.of(), List.of(expiredOffer), List.of(10L), now))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void endedCouponTemplateSkipped() {
        CouponTemplate endedTemplate = new CouponTemplate(1L, 0L, "已下架券", Money.of("0.00"),
                Money.of("30.00"), Scope.all(), 100, 0, 2, null, null,
                now.minusDays(1), now.plusDays(30), CouponTemplate.STATUS_ENDED);
        CouponOffer offer = new CouponOffer(
                new UserCoupon(10L, 1L, 1L, CouponStatus.UNUSED, null, now.minusDays(1), null, now.plusDays(30)),
                endedTemplate);

        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)), List.of(), List.of(offer), null, now);
        assertThat(result.coupons()).isEmpty();
    }

    @Test
    void couponCategoryScopeThresholdJudgedOnMatchedSubset() {
        // 券满 150 可用、限分类 7：分类 7 小计 100 < 150 → 自动选券放弃
        CouponTemplate scoped = new CouponTemplate(1L, 0L, "分类券", Money.of("150.00"),
                Money.of("30.00"), new Scope(ScopeType.CATEGORY, Set.of(7L)), 100, 0, 2,
                null, null, now.minusDays(1), now.plusDays(30), CouponTemplate.STATUS_ACTIVE);
        CouponOffer offer = new CouponOffer(
                new UserCoupon(10L, 1L, 1L, CouponStatus.UNUSED, null, now.minusDays(1), null, now.plusDays(30)),
                scoped);

        DiscountSelection below = service.calculate(
                List.of(line(1L, 7L, 1L, "100.00", 1), line(2L, 8L, 1L, "900.00", 1)),
                List.of(), List.of(offer), null, now);
        assertThat(below.coupons()).isEmpty();
        assertThat(below.couponDiscount().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void promotionCategoryThresholdNotHelpedByOtherLines() {
        // 活动满 150 减 50、限分类 7：全车 1000 但命中分类只有 100 → 不生效
        Promotion promo = promotion(1L, fullReduction("150.00", 0, "50.00"),
                new Scope(ScopeType.CATEGORY, Set.of(7L)), true, null);
        DiscountSelection result = service.calculate(
                List.of(line(1L, 7L, 1L, "100.00", 1), line(2L, 8L, 1L, "900.00", 1)),
                List.of(promo), List.of(), null, now);
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.promotions()).isEmpty();
    }

    @Test
    void couponCappedByOriginalEvenWithoutPromotion() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "150.00")), null, now);
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.payAmount().amount()).isEqualByComparingTo("0.00");
    }

    // ==================== 边界补充：叠加裁决（活动侧配置） ====================

    @Test
    void stackingDisabledWithEqualTotalKeepsPromotionSavingCoupon() {
        // 活动不让叠券且两者优惠相等 → 保留活动方案（券留给用户下次用）
        Promotion promo = promotion(1L, fullReduction("0.00", 0, "60.00"), Scope.all(), false, null);
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promo), List.of(offer(10L, 1L, "0.00", "60.00")), null, now);
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("60.00");
        assertThat(result.coupons()).isEmpty();
        assertThat(result.promotionCouponStackable()).isFalse();
    }

    @Test
    void stackingDisabledWithoutUsableCouponsKeepsPromotion() {
        Promotion promo = promotion(1L, fullReduction("0.00", 0, "60.00"), Scope.all(), false, null);
        // 券门槛 1500 > 小计 1000 → 无可用券，活动方案保留
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promo), List.of(offer(10L, 1L, "1500.00", "60.00")), null, now);
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("60.00");
        assertThat(result.coupons()).isEmpty();
        assertThat(result.promotionCouponStackable()).isFalse();
    }

    @Test
    void stackingAllowedWhenNoPromotionApplies() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("2000.00", 0, "50.00"), Scope.all(), false, null)),
                List.of(offer(10L, 1L, "0.00", "30.00")), null, now);
        // 活动门槛不满足 = 无生效活动 → 券不受「不可叠加」限制
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("30.00");
        assertThat(result.promotionCouponStackable()).isTrue();
    }

    @Test
    void stackingDisabledWhenAnyAppliedPromotionDisallows() {
        // 平台活动可叠 + 商家活动不可叠 → 只要有一个生效活动不可叠，整体不叠
        Promotion platform = promotion(1L, fullReduction("0.00", 0, "10.00"),
                new Scope(ScopeType.PRODUCT, Set.of(2L)), true, null);
        Promotion stackableMerchant = merchantPromotion(11L, 100L, fullReduction("0.00", 0, "60.00"), Scope.shop());
        Promotion merchant = new Promotion(11L, 100L, stackableMerchant.getTitle(), stackableMerchant.getType(),
                stackableMerchant.getRule(), stackableMerchant.getScope(), false, false, null,
                stackableMerchant.getStartTime(), stackableMerchant.getEndTime(),
                stackableMerchant.getStatus(), "", "");

        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 100L, "500.00", 1), line(2L, 2L, 200L, "500.00", 1)),
                List.of(platform, merchant), List.of(offer(10L, 1L, "0.00", "30.00")), null, now);
        // 活动合计 70 优于券 30 → 保活动弃券
        assertThat(result.promotionDiscount().amount()).isEqualByComparingTo("70.00");
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("0.00");
        assertThat(result.promotionCouponStackable()).isFalse();
    }

    // ==================== 券侧叠加配置（couponStackable / stackable） ====================

    @Test
    void couponNotActivityStackableAutoPicksBetterSide() {
        // 券不可与活动同用（couponStackable=false）：券 150 优于活动 100 → 仅用券
        DiscountSelection couponWins = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null)),
                List.of(offerWith(10L, 1L, "0.00", "150.00", false, true)), null, now);
        assertThat(couponWins.promotions()).isEmpty();
        assertThat(couponWins.couponDiscount().amount()).isEqualByComparingTo("150.00");
        // 活动被静默舍弃 → 提示未叠加
        assertThat(couponWins.promotionCouponStackable()).isFalse();

        // 反向：活动 100 优于券 40 → 仅用活动，券被静默舍弃
        DiscountSelection promoWins = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null)),
                List.of(offerWith(10L, 1L, "0.00", "40.00", false, true)), null, now);
        assertThat(promoWins.promotions()).extracting(Promotion::getId).containsExactly(1L);
        assertThat(promoWins.coupons()).isEmpty();
        assertThat(promoWins.promotionDiscount().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void couponNotActivityStackableRequestedThrows() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), true, null)),
                List.of(offerWith(10L, 1L, "0.00", "40.00", false, true)),
                List.of(10L), now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可与促销活动叠加使用");
    }

    @Test
    void promotionDisallowingStackingRequestedThrows() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "100.00"), Scope.all(), false, null)),
                List.of(offer(10L, 1L, "0.00", "40.00")),
                List.of(10L), now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前促销活动不可与优惠券叠加使用");
    }

    @Test
    void nonStackableCouponAutoSelectedSoloWhenBetter() {
        // 不可叠加券 60 单独使用，优于可叠券组 30+20=50
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offerWith(10L, 1L, "0.00", "60.00", true, false),
                        offer(11L, 2L, "0.00", "30.00"),
                        offer(12L, 3L, "0.00", "20.00")),
                null, now);
        assertThat(result.coupons()).extracting(offer -> offer.coupon().getId()).containsExactly(10L);
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void singleNonStackableCouponRequestedStillApplies() {
        // 单独使用一张不可叠加券是允许的（限制只在多张之间）
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offerWith(10L, 1L, "0.00", "60.00", true, false)),
                List.of(10L), now);
        assertThat(result.coupons()).extracting(offer -> offer.coupon().getId()).containsExactly(10L);
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("60.00");
    }

    @Test
    void nonStackableCouponRequestedWithOthersThrows() {
        assertThatThrownBy(() -> service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offerWith(10L, 1L, "0.00", "60.00", true, false),
                        offer(11L, 2L, "0.00", "30.00")),
                List.of(10L, 11L), now))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可与其他优惠券叠加使用");
    }

    @Test
    void stackableCouponsStillCombineWithoutPromotion() {
        // 可叠加券（stackable=true 默认）之间照常多张组合
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "30.00"), offer(11L, 2L, "0.00", "20.00")),
                null, now);
        assertThat(result.couponDiscount().amount()).isEqualByComparingTo("50.00");
        assertThat(result.coupons()).hasSize(2);
    }

    // ==================== 边界补充：费用承担 ====================

    @Test
    void bearSplitRoundsHalfUpWithRemainderToMerchant() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "33.33"), Scope.all(), false,
                        new BigDecimal("50"))),
                List.of(), null, now);
        assertThat(result.platformBearAmount().amount()).isEqualByComparingTo("16.67");
        assertThat(result.merchantBearAmount().amount()).isEqualByComparingTo("16.66");
        assertThat(result.platformBearAmount().add(result.merchantBearAmount()).amount())
                .isEqualByComparingTo("33.33");
    }

    @Test
    void bearSplitWithTruncatedCouponIncludesCouponOwnership() {
        // 活动优惠 60（平台 50% → 30/30）+ 券被预算截断为 40（平台券 → 平台全担）
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "60.00"), Scope.all(), true,
                        new BigDecimal("50"))),
                List.of(offer(10L, 1L, "0.00", "50.00")), null, now);
        assertThat(result.totalDiscount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.platformBearAmount().amount()).isEqualByComparingTo("70.00");
        assertThat(result.merchantBearAmount().amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void platformBearNeverExceedsTotalDiscount() {
        // 平台活动 100% + 平台券：承担全部优惠，商家承担为 0
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "100.00", 1)),
                List.of(promotion(1L, fullReduction("0.00", 0, "60.00"), Scope.all(), true,
                        new BigDecimal("100"))),
                List.of(offer(10L, 1L, "0.00", "40.00")), null, now);
        assertThat(result.platformBearAmount().add(result.merchantBearAmount()).amount())
                .isEqualByComparingTo(result.totalDiscount().amount());
        assertThat(result.platformBearAmount().amount()).isEqualByComparingTo("100.00");
        assertThat(result.merchantBearAmount().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void primaryCouponIsTheLargestHit() {
        DiscountSelection result = service.calculate(
                List.of(line(1L, 1L, 1L, "1000.00", 1)),
                List.of(),
                List.of(offer(10L, 1L, "0.00", "30.00"), offer(11L, 1L, "0.00", "50.00")), null, now);
        assertThat(result.primaryCoupon()).map(offer -> offer.coupon().getId()).contains(11L);
        // 输出按券 id 升序，记账顺序稳定
        assertThat(result.coupons()).extracting(offer -> offer.coupon().getId()).containsExactly(10L, 11L);
        assertThat(result.couponHits()).extracting(hit -> hit.amount().intValue())
                .containsExactly(30, 50);
    }

    // ==================== 构造辅助 ====================

    private OrderLine line(Long productId, Long categoryId, Long shopId, String price, int quantity) {
        return new OrderLine(productId, categoryId, shopId, Money.of(price), quantity);
    }

    private DiscountRule fullReduction(String threshold, int perEvery, String amount) {
        return new DiscountRule(RuleType.FULL_REDUCTION, Money.of(threshold), perEvery,
                Money.of(amount), null, null);
    }

    /** 平台活动（shopId=0），platformBearRate 为 null 时商家全额承担 */
    private Promotion promotion(Long id, DiscountRule rule, Scope scope, boolean stackable,
                                BigDecimal platformBearRate) {
        return new Promotion(id, 0L, "活动" + id, "平台大促", rule, scope, stackable,
                false, platformBearRate, now.minusDays(1), now.plusDays(1),
                Promotion.STATUS_ACTIVE, "", "");
    }

    /** 商家自建活动：范围限本店，费用商家全额承担 */
    private Promotion merchantPromotion(Long id, Long shopId, DiscountRule rule, Scope scope) {
        return new Promotion(id, shopId, "店铺活动" + id, "店铺促销", rule, scope, true,
                false, null, now.minusDays(1), now.plusDays(1),
                Promotion.STATUS_ACTIVE, "", "");
    }

    private CouponOffer offer(Long couponId, Long templateId, String threshold, String amount) {
        return offerWith(couponId, templateId, threshold, amount, null, null);
    }

    /** 指定叠加配置的券（null 走默认：可与活动同用、可与其他券叠加） */
    private CouponOffer offerWith(Long couponId, Long templateId, String threshold, String amount,
                                  Boolean couponStackable, Boolean stackable) {
        CouponTemplate template = new CouponTemplate(templateId, 0L, "优惠券", Money.of(threshold),
                Money.of(amount), Scope.all(), 100, 0, 2, couponStackable, stackable,
                now.minusDays(1), now.plusDays(30), CouponTemplate.STATUS_ACTIVE);
        UserCoupon coupon = new UserCoupon(couponId, 1L, templateId, CouponStatus.UNUSED, null,
                now.minusDays(1), null, now.plusDays(30));
        return new CouponOffer(coupon, template);
    }

    /** 商家券：范围限本店 */
    private CouponOffer merchantOffer(Long couponId, Long shopId, String threshold, String amount) {
        CouponTemplate template = new CouponTemplate(20L, shopId, "商家券", Money.of(threshold),
                Money.of(amount), Scope.shop(), 100, 0, 2, null, null,
                now.minusDays(1), now.plusDays(30), CouponTemplate.STATUS_ACTIVE);
        UserCoupon coupon = new UserCoupon(couponId, 1L, 20L, CouponStatus.UNUSED, null,
                now.minusDays(1), null, now.plusDays(30));
        return new CouponOffer(coupon, template);
    }
}
