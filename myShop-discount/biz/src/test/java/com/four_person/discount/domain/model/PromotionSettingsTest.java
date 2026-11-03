package com.four_person.discount.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 折扣规则设置归一：上限收敛、多券开关联动。
 */
class PromotionSettingsTest {

    @Test
    void defaultsAllowStackingWithThreeCoupons() {
        PromotionSettings settings = PromotionSettings.defaults();
        assertThat(settings.couponStackable()).isTrue();
        assertThat(settings.multiCouponEnabled()).isTrue();
        assertThat(settings.maxCouponsPerOrder()).isEqualTo(3);
    }

    @Test
    void maxBelowOneClampsToOne() {
        assertThat(new PromotionSettings(true, true, 0).maxCouponsPerOrder()).isEqualTo(1);
        assertThat(new PromotionSettings(true, true, -5).maxCouponsPerOrder()).isEqualTo(1);
    }

    @Test
    void multiCouponDisabledForcesSingleCoupon() {
        PromotionSettings settings = new PromotionSettings(true, false, 5);
        assertThat(settings.multiCouponEnabled()).isFalse();
        assertThat(settings.maxCouponsPerOrder()).isEqualTo(1);
    }

    @Test
    void enabledKeepsConfiguredMax() {
        assertThat(new PromotionSettings(true, true, 10).maxCouponsPerOrder()).isEqualTo(10);
    }
}
