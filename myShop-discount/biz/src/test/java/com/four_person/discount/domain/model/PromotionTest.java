package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.ScopeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 活动聚合根边界：构造校验、商家活动强制归一、生效时间相等边界、承担比例舍入与封顶。
 */
class PromotionTest {

    private final LocalDateTime now = LocalDateTime.now();

    private Promotion promotion(Long shopId, DiscountRule rule, Scope scope, boolean stackable,
                                boolean enrollRequired, BigDecimal bearRate,
                                LocalDateTime start, LocalDateTime end, String status) {
        return new Promotion(1L, shopId, "BBT-活动", null, rule, scope, stackable, enrollRequired,
                bearRate, start, end, status, "", "");
    }

    private Promotion platform(BigDecimal bearRate) {
        return promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("10.00")),
                Scope.all(), true, false, bearRate, now.minusDays(1), now.plusDays(1), null);
    }

    // ==================== 构造校验 ====================

    @Test
    void shouldRejectBlankTitleAndNullRule() {
        assertThatThrownBy(() -> new Promotion(1L, 0L, "  ", null, null, Scope.all(), true,
                false, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动名称不能为空");
        assertThatThrownBy(() -> promotion(0L, null, Scope.all(), true, false, null,
                null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动规则不能为空");
    }

    @Test
    void shouldRejectBearRateOutsideZeroToHundred() {
        assertThatThrownBy(() -> platform(new BigDecimal("-0.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台承担比例必须在 0~100 之间");
        assertThatThrownBy(() -> platform(new BigDecimal("100.01")))
                .isInstanceOf(BusinessException.class);
        // 边界值 0 与 100 合法
        assertThat(platform(BigDecimal.ZERO).getPlatformBearRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(platform(new BigDecimal("100")).getPlatformBearRate()).isEqualByComparingTo("100");
    }

    @Test
    void shouldRejectInvertedOrInvalidTimeWindow() {
        assertThatThrownBy(() -> promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, now.plusDays(1), now.minusDays(1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始时间不能晚于结束时间");
        // 起止相等合法
        assertThat(promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, now, now, null).isActiveAt(now)).isTrue();
    }

    @Test
    void shouldDefaultTypeStatusAndTrimTitle() {
        Promotion platform = new Promotion(1L, 0L, "  双11  ", null,
                DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")), Scope.all(), true,
                false, null, null, null, "", null, null);
        assertThat(platform.getTitle()).isEqualTo("双11");
        assertThat(platform.getType()).isEqualTo("平台大促");
        assertThat(platform.getStatus()).isEqualTo(Promotion.STATUS_ACTIVE);
        assertThat(platform.getDescription()).isEmpty();
        assertThat(platform.getColor()).isEmpty();

        Promotion merchant = new Promotion(2L, 5L, "店铺活动", null,
                DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")), Scope.shop(), true,
                false, null, null, null, null, null, null);
        assertThat(merchant.getType()).isEqualTo("店铺促销");
    }

    // ==================== 商家活动强制归一 ====================

    @Test
    void merchantActivityMustBeShopOrProductScope() {
        assertThatThrownBy(() -> promotion(5L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商家活动范围只能是本店商品");
        assertThatThrownBy(() -> promotion(5L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                new Scope(ScopeType.CATEGORY, Set.of(7L)), true, false, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void merchantActivityForcesMerchantFullBearAndNoEnrollment() {
        Promotion merchant = promotion(5L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.shop(), true, true, new BigDecimal("60"), null, null, null);
        assertThat(merchant.getPlatformBearRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(merchant.isEnrollRequired()).isFalse();
        assertThat(merchant.isMerchantActivity()).isTrue();
        // 商家活动费用永远全部商家承担
        assertThat(merchant.platformBearOf(Money.of("100.00")).amount()).isEqualByComparingTo("0.00");
        assertThat(merchant.merchantBearOf(Money.of("100.00")).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void platformActivityCanRequireEnrollment() {
        Promotion promo = promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, true, null, null, null, null);
        assertThat(promo.isEnrollRequired()).isTrue();
        assertThat(promo.isMerchantActivity()).isFalse();
    }

    // ==================== 生效时间边界 ====================

    @Test
    void isActiveAtHandlesExactBoundaries() {
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        Promotion promo = promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, start, end, null);
        assertThat(promo.isActiveAt(start)).isTrue();
        assertThat(promo.isActiveAt(start.minusSeconds(1))).isFalse();
        assertThat(promo.isActiveAt(end)).isTrue();
        assertThat(promo.isActiveAt(end.plusSeconds(1))).isFalse();
    }

    @Test
    void inactiveWhenEndedOrWrongStatus() {
        assertThat(promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, null, null, Promotion.STATUS_ENDED).isActiveAt(now)).isFalse();
        assertThat(promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, now.plusSeconds(1), null, null).isActiveAt(now)).isFalse();
        assertThat(promotion(0L, DiscountRule.fullReduction(Money.zero(), 0, Money.of("1.00")),
                Scope.all(), true, false, null, null, now.minusSeconds(1), null).isActiveAt(now)).isFalse();
    }

    // ==================== 费用承担舍入 ====================

    @Test
    void platformBearOfRoundsHalfUpAndCaps() {
        // 33.33 × 50% = 16.665 → HALF_UP 16.67
        assertThat(platform(new BigDecimal("50")).platformBearOf(Money.of("33.33")).amount())
                .isEqualByComparingTo("16.67");
        // 100 × 33% = 33.00
        assertThat(platform(new BigDecimal("33")).platformBearOf(Money.of("100.00")).amount())
                .isEqualByComparingTo("33.00");
        // 100% 不超过优惠额
        assertThat(platform(new BigDecimal("100")).platformBearOf(Money.of("88.88")).amount())
                .isEqualByComparingTo("88.88");
        // 0 优惠 / 0 比例 → 0
        assertThat(platform(new BigDecimal("50")).platformBearOf(Money.zero()).amount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void merchantBearComplementsPlatformBearExactly() {
        Promotion promo = platform(new BigDecimal("50"));
        Money discount = Money.of("33.33");
        assertThat(promo.platformBearOf(discount).add(promo.merchantBearOf(discount)).amount())
                .isEqualByComparingTo("33.33");
        assertThat(promo.merchantBearOf(discount).amount()).isEqualByComparingTo("16.66");
    }

    // ==================== 范围收窄与拷贝 ====================

    @Test
    void narrowedToProductsReplacesScopeAndKeepsBearRate() {
        Promotion promo = platform(new BigDecimal("30"));
        Promotion narrowed = promo.narrowedToProducts(Set.of(101L, 102L));
        assertThat(narrowed.getScope().type()).isEqualTo(ScopeType.PRODUCT);
        assertThat(narrowed.getScope().ids()).containsExactlyInAnyOrder(101L, 102L);
        assertThat(narrowed.getPlatformBearRate()).isEqualByComparingTo("30");
        assertThat(narrowed.isEnrollRequired()).isFalse();
        assertThat(narrowed.getId()).isEqualTo(promo.getId());
        assertThat(narrowed.isCouponStackable()).isEqualTo(promo.isCouponStackable());
    }

    @Test
    void narrowedToProductsRejectsEmptySet() {
        assertThatThrownBy(() -> platform(null).narrowedToProducts(Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("指定范围必须配置范围 ID");
    }

    @Test
    void endedAndWithIdCopyEverything() {
        Promotion promo = platform(new BigDecimal("30"));
        Promotion ended = promo.ended();
        assertThat(ended.getStatus()).isEqualTo(Promotion.STATUS_ENDED);
        assertThat(ended.getTitle()).isEqualTo(promo.getTitle());

        Promotion renumbered = promo.withId(99L);
        assertThat(renumbered.getId()).isEqualTo(99L);
        assertThat(renumbered.getStatus()).isEqualTo(Promotion.STATUS_ACTIVE);
    }
}
