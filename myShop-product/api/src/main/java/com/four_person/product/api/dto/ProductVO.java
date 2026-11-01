package com.four_person.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private Long shopId;

    private String shopName;

    private String name;

    private BigDecimal price;

    private BigDecimal originPrice;

    private Integer stock;

    private Integer sales;

    private BigDecimal rating;

    private String emoji;

    private String color;

    private List<String> tags;

    private String description;

    private List<String> specs;

    private Integer status;
}
