package com.four_person.discount.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户券 + 券模板的组合，用于算价。
 */
public record CouponOffer(UserCoupon coupon, CouponTemplate template) {

    public boolean isUsable(LocalDateTime now) {
        return coupon.isUsable(now) && template.isActiveAt(now);
    }

    public Money discountFor(List<OrderLine> lines) {
        return template.discountFor(lines);
    }

    /** 平台券优惠由平台全额承担，商家券由商家全额承担 */
    public Money platformBearOf(Money discount) {
        return template.isMerchantCoupon() ? Money.zero() : discount;
    }

    /** 该券是否可与促销活动同用（取模板配置） */
    public boolean isActivityStackable() {
        return template.isCouponStackable();
    }

    /** 该券是否可与其他优惠券叠加（取模板配置） */
    public boolean isStackable() {
        return template.isStackable();
    }
}
