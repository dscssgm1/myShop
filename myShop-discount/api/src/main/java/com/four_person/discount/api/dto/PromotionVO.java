package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PromotionVO {

    private Long id;

    private String title;

    private String type;

    private String ruleType;

    private BigDecimal thresholdAmount;

    private Integer perEvery;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private BigDecimal maxDiscount;

    private String scopeType;

    private List<Long> scopeIds;

    private Boolean couponStackable;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private String statusText;

    private String description;

    private String color;
}
