package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.ScopeType;
import org.junit.jupiter.api.Test;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 优惠券模板边界：构造校验、可领时间/库存/限领的相等边界、用券门槛边界。
 */
class CouponTemplateTest {

    private final LocalDateTime now = LocalDateTime.now();

    private CouponTemplate template(Long shopId, String threshold, String amount, Scope scope,
                                    int totalStock, int claimedCount, int perUserLimit,
                                    LocalDateTime validStart, LocalDateTime validEnd, String status) {
        return new CouponTemplate(1L, shopId, "BBT-测试券", Money.of(threshold), Money.of(amount), scope,
                totalStock, claimedCount, perUserLimit, null, null, validStart, validEnd, status);
    }

    // ==================== 构造校验 ====================

    @Test
    void shouldRejectInvalidConstruction() {
        assertThatThrownBy(() -> new CouponTemplate(1L, 0L, " ", Money.zero(), Money.of("5.00"), Scope.all(),
                10, 0, 1, null, null, null, null, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> template(0L, "10", "0", Scope.all(), 10, 0, 1, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> template(0L, "10", "-1", Scope.all(), 10, 0, 1, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> template(0L, "10", "5", Scope.all(), 0, 0, 1, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> template(0L, "10", "5", Scope.all(), 10, 0, 0, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectInvertedValidityWindow() {
        assertThatThrownBy(() -> template(0L, "10", "5", Scope.all(), 10, 0, 1,
                now.plusDays(2), now.plusDays(1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效期开始时间不能晚于结束时间");
    }

    @Test
    void shouldRestrictMerchantCouponScope() {
        // 商家券只能圈本店：全平台 / 指定商品都不允许
        assertThatThrownBy(() -> template(5L, "10", "5", Scope.all(), 10, 0, 1, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商家优惠券范围只能是本店商品");
        assertThatThrownBy(() -> template(5L, "10", "5", new Scope(ScopeType.PRODUCT, Set.of(9L)),
                10, 0, 1, null, null, null)).isInstanceOf(BusinessException.class);
        // SHOP / CATEGORY（本店指定分类）合法
        assertThat(template(5L, "10", "5", Scope.shop(), 10, 0, 1, null, null, null).isMerchantCoupon()).isTrue();
        assertThat(template(5L, "10", "5", new Scope(ScopeType.CATEGORY, Set.of(7L)),
                10, 0, 1, null, null, null).isMerchantCoupon()).isTrue();
    }

    @Test
    void shouldDefaultNullThresholdAndStatus() {
        CouponTemplate template = new CouponTemplate(1L, 0L, "券", null, Money.of("5.00"), Scope.all(),
                10, 0, 1, null, null, null, null, null);
        assertThat(template.getThresholdAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(template.getStatus()).isEqualTo(CouponTemplate.STATUS_ACTIVE);
    }

    // ==================== 叠加配置 ====================

    @Test
    void stackingFlagsDefaultTrueWhenNull() {
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 10, 0, 1, null, null, null);
        assertThat(template.isCouponStackable()).isTrue();
        assertThat(template.isStackable()).isTrue();
    }

    @Test
    void stackingFlagsKeepExplicitValues() {
        CouponTemplate noActivity = new CouponTemplate(1L, 0L, "券", Money.zero(), Money.of("5.00"), Scope.all(),
                10, 0, 1, false, true, null, null, null);
        assertThat(noActivity.isCouponStackable()).isFalse();
        assertThat(noActivity.isStackable()).isTrue();

        CouponTemplate soloOnly = new CouponTemplate(1L, 0L, "券", Money.zero(), Money.of("5.00"), Scope.all(),
                10, 0, 1, true, false, null, null, null);
        assertThat(soloOnly.isCouponStackable()).isTrue();
        assertThat(soloOnly.isStackable()).isFalse();
    }

    // ==================== 活动中的时间边界 ====================

    @Test
    void isActiveAtHandlesExactBoundaries() {
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 10, 0, 1, start, end, null);

        assertThat(template.isActiveAt(start)).isTrue();
        assertThat(template.isActiveAt(start.minusSeconds(1))).isFalse();
        assertThat(template.isActiveAt(end)).isTrue();
        assertThat(template.isActiveAt(end.plusSeconds(1))).isFalse();
        assertThat(template.isActiveAt(now)).isTrue();
    }

    @Test
    void isActiveAtWithoutBoundsIsAlwaysTrueWhileActive() {
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 10, 0, 1, null, null, null);
        assertThat(template.isActiveAt(now.plusYears(5))).isTrue();
    }

    @Test
    void isActiveAtFalseWhenEnded() {
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 10, 0, 1, null, null,
                CouponTemplate.STATUS_ENDED);
        assertThat(template.isActiveAt(now)).isFalse();
    }

    // ==================== 领取边界 ====================

    @Test
    void canClaimHandlesExactLimitBoundaries() {
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 2, 1, 2, null, null, null);
        // 已领 1/限 2：还能领第 2 张
        assertThat(template.canClaim(1, now)).isTrue();
        // 用户已领满 2 张
        assertThat(template.canClaim(2, now)).isFalse();
        // 库存领完（claimedCount == totalStock）
        CouponTemplate empty = template(0L, "0", "5", Scope.all(), 2, 2, 5, null, null, null);
        assertThat(empty.canClaim(0, now)).isFalse();
        // 最后一张库存
        assertThat(template(0L, "0", "5", Scope.all(), 2, 1, 5, null, null, null).canClaim(0, now)).isTrue();
    }

    @Test
    void ensureClaimableThrowsDistinctErrorCodes() {
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        BusinessException notInTime = catchOf(template(0L, "0", "5", Scope.all(), 10, 0, 1,
                now.plusDays(1), null, null), 0);
        assertThat(notInTime.getCode()).isEqualTo(DiscountErrorCode.COUPON_NOT_AVAILABLE.getCode());

        BusinessException limit = catchOf(template(0L, "0", "5", Scope.all(), 10, 0, 1, start, end, null), 1);
        assertThat(limit.getCode()).isEqualTo(DiscountErrorCode.CLAIM_LIMIT_REACHED.getCode());

        BusinessException stock = catchOf(template(0L, "0", "5", Scope.all(), 1, 1, 1, start, end, null), 0);
        assertThat(stock.getCode()).isEqualTo(DiscountErrorCode.COUPON_STOCK_EMPTY.getCode());
    }

    private BusinessException catchOf(CouponTemplate template, int claimedByUser) {
        try {
            template.ensureClaimable(claimedByUser, now);
        } catch (BusinessException ex) {
            return ex;
        }
        return org.assertj.core.api.Assertions.fail("应当抛出业务异常");
    }

    // ==================== 用券门槛 ====================

    @Test
    void discountForHonorsThresholdBoundaryAndCap() {
        CouponTemplate template = template(0L, "100.00", "30.00", Scope.all(), 10, 0, 1, null, null, null);
        assertThat(template.discountFor(lines("99.99")).amount()).isEqualByComparingTo("0.00");
        assertThat(template.discountFor(lines("100.00")).amount()).isEqualByComparingTo("30.00");

        // 无门槛券面额大于范围内小计 → 封顶为小计
        CouponTemplate noThreshold = template(0L, "0.00", "30.00", Scope.all(), 10, 0, 1, null, null, null);
        assertThat(noThreshold.discountFor(lines("20.00")).amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void discountForOnlyCountsInScopeSubtotal() {
        CouponTemplate template = template(100L, "150.00", "30.00", Scope.shop(), 10, 0, 1, null, null, null);
        List<OrderLine> lines = List.of(
                new OrderLine(1L, 7L, 100L, Money.of("100.00"), 1),
                new OrderLine(2L, 7L, 200L, Money.of("900.00"), 1));
        // 本店只有 100，达不到 150 门槛；他店 900 不参与
        assertThat(template.discountFor(lines).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void remainingNeverNegative() {
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 100, 150, 1, null, null, null);
        assertThat(template.getRemaining()).isZero();
    }

    @Test
    void endedCopyKeepsEverythingButStatus() {
        CouponTemplate template = template(0L, "0", "5", Scope.all(), 10, 3, 1, null, null, null);
        CouponTemplate ended = template.ended();
        assertThat(ended.getStatus()).isEqualTo(CouponTemplate.STATUS_ENDED);
        assertThat(ended.getId()).isEqualTo(template.getId());
        assertThat(ended.getClaimedCount()).isEqualTo(3);
    }

    private List<OrderLine> lines(String subtotal) {
        return List.of(new OrderLine(1L, 7L, 100L, Money.of(subtotal), 1));
    }
}
