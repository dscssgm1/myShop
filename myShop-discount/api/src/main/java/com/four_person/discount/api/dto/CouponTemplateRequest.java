package com.four_person.discount.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CouponTemplateRequest {

    @NotBlank(message = "优惠券名称不能为空")
    private String title;

    @NotNull(message = "使用门槛不能为空")
    @DecimalMin(value = "0.00", message = "使用门槛不能为负数")
    private BigDecimal thresholdAmount;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额必须大于 0")
    private BigDecimal discountAmount;

    /** ALL / CATEGORY / PRODUCT */
    private String scopeType;

    private List<Long> scopeIds;

    @NotNull(message = "发放总量不能为空")
    @Min(value = 1, message = "发放总量必须大于 0")
    private Integer totalStock;

    @NotNull(message = "每人限领数量不能为空")
    @Min(value = 1, message = "每人限领数量必须大于 0")
    private Integer perUserLimit;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private String status;
}
