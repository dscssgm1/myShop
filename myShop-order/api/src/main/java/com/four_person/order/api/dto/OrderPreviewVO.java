package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderPreviewVO {

    private List<OrderPreviewItemVO> items;

    /** 按店铺拆分的预览 */
    private List<ShopPreviewVO> shops;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    /** 优惠中商家承担的部分 */
    private BigDecimal merchantBearAmount;

    /** 优惠中平台承担（补贴）的部分 */
    private BigDecimal platformBearAmount;

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    /** 主优惠券（优惠额最大的一张），展示用；完整列表见 appliedCoupons */
    private Long appliedCouponId;

    private String appliedCouponTitle;

    /** 实际生效的优惠券明细（一单多券） */
    private List<CouponUsageVO> appliedCoupons;

    @Data
    public static class CouponUsageVO {
        private Long couponId;
        private String title;
        private BigDecimal discount;
    }

    @Data
    public static class ShopPreviewVO {
        private Long shopId;
        private String shopName;
        private BigDecimal originalAmount;
        private BigDecimal promotionDiscount;
        private BigDecimal couponDiscount;
        private BigDecimal freightAmount;
        private BigDecimal payAmount;
        private List<OrderPreviewItemVO> items;
    }
}
