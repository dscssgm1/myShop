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

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    private Long appliedCouponId;

    private String appliedCouponTitle;

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
