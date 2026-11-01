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
}
