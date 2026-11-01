package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecommendationVO {

    private Long productId;

    private String name;

    private BigDecimal price;

    private String emoji;

    private String color;

    private String shopName;

    private Long categoryId;

    private String categoryName;

    private Integer sales;

    private BigDecimal rating;

    private BigDecimal score;

    private String reason;
}
