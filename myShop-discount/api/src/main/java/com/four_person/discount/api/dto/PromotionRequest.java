package com.four_person.discount.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PromotionRequest {

    @NotBlank(message = "活动名称不能为空")
    private String title;

    /** 展示类型：平台大促 / 品类促销 / 限时秒杀 / 领券中心 */
    private String type;

    /** FULL_REDUCTION / DISCOUNT */
    @NotBlank(message = "优惠规则类型不能为空")
    private String ruleType;

    private BigDecimal thresholdAmount;

    private Integer perEvery;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private BigDecimal maxDiscount;

    /** ALL / CATEGORY / PRODUCT */
    private String scopeType;

    private List<Long> scopeIds;

    private Boolean couponStackable;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private String description;

    private String color;
}
