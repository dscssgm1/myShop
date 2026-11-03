package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DiscountResultVO {

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal payAmount;

    /** 实际生效的优惠金额中商家承担的部分 */
    private BigDecimal merchantBearAmount;

    /** 实际生效的优惠金额中平台承担（补贴）的部分 */
    private BigDecimal platformBearAmount;

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    private Boolean promotionCouponStackable;

    /** 主优惠券（优惠额最大的一张），展示用；完整列表见 appliedCoupons */
    private Long appliedCouponId;

    private String appliedCouponTitle;

    /** 实际生效的优惠券明细（一单多券） */
    private List<CouponUsage> appliedCoupons;

    /** 一张生效优惠券在本单的抵扣明细 */
    @Data
    public static class CouponUsage {

        private Long couponId;

        private String title;

        private BigDecimal discount;
    }
}
