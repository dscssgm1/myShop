package com.four_person.discount.domain.model;

/**
 * 一次算价的结果值对象。
 */
public record DiscountSelection(Money originalAmount,
                               Money promotionDiscount,
                               Money couponDiscount,
                               Long promotionId,
                               String promotionTitle,
                               boolean promotionCouponStackable,
                               Long couponId,
                               String couponTitle) {

    public Money totalDiscount() {
        return promotionDiscount.add(couponDiscount);
    }

    public Money payAmount() {
        return originalAmount.subtract(totalDiscount()).min(originalAmount);
    }
}
