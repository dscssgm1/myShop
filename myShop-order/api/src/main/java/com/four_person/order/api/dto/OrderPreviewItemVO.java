package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;

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
}
