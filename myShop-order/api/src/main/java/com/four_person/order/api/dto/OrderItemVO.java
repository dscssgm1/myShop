package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemVO {

    private Long productId;

    private Long skuId;

    private String skuSpecs;

    private String productName;

    private String productEmoji;

    private String productColor;

    /** 商品主图快照 */
    private String productCoverImage;

    private Long shopId;

    private String shopName;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    /** 本行促销活动优惠金额（下单时行级分摊快照） */
    private BigDecimal promotionDiscount;

    /** 本行优惠券抵扣金额（下单时行级分摊快照） */
    private BigDecimal couponDiscount;
}
