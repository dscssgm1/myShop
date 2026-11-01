package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemVO {

    private Long id;

    private Long productId;

    private Long skuId;

    private String skuSpecs;

    private String productName;

    private BigDecimal price;

    private Integer quantity;

    private Long shopId;

    private String shopName;

    private String emoji;

    private String color;

    private BigDecimal subtotal;

    private Integer stock;
}
