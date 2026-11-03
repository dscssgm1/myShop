package com.four_person.discount.domain.model;

/**
 * 叠加规则设置（运营配置 discount 组），一次算价的边界条件。
 */
public record PromotionSettings(boolean couponStackable,
                                boolean multiCouponEnabled,
                                int maxCouponsPerOrder) {

    public PromotionSettings {
        if (maxCouponsPerOrder < 1) {
            maxCouponsPerOrder = 1;
        }
        // 不允许多券叠加时，上限收敛为 1 张
        if (!multiCouponEnabled) {
            maxCouponsPerOrder = 1;
        }
    }

    public static PromotionSettings defaults() {
        return new PromotionSettings(true, true, 3);
    }
}
