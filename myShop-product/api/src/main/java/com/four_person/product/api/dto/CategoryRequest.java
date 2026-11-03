package com.four_person.product.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryRequest {

    /** 0 表示大类，否则为父分类 id */
    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private String icon;

    private String color;

    private Integer sort = 0;

    /** 平台佣金费率（%）；不传表示不抽佣，小类传 null 表示继承大类 */
    @DecimalMin(value = "0", message = "佣金费率不能小于 0")
    @DecimalMax(value = "100", message = "佣金费率不能大于 100")
    private BigDecimal commissionRate;
}
