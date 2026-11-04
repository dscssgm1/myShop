package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderPreviewItemVO {

    private Long productId;

    private Long skuId;

    private String skuSpecs;

    private String productName;

    private String productEmoji;

    private String productColor;

    /** 商品主图（结算页实时取自商品） */
    private String productCoverImage;

    private Long shopId;

    private String shopName;

    private Long categoryId;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    /** 本行参与促销活动的优惠金额 */
    private BigDecimal promotionDiscount;

    /** 本行优惠券抵扣金额 */
    private BigDecimal couponDiscount;

    /** 优惠后应付小计 = subtotal - promotionDiscount - couponDiscount */
    private BigDecimal finalSubtotal;

    /** 本行参与的促销活动明细 */
    private List<AppliedHitVO> itemPromotions;

    /** 本行参与的优惠券明细 */
    private List<AppliedHitVO> itemCoupons;

    /** 单条优惠命中（活动或券）：id + 名称 + 本行抵扣金额 */
    @Data
    public static class AppliedHitVO {
        private Long id;
        private String title;
        private BigDecimal amount;
    }
}
