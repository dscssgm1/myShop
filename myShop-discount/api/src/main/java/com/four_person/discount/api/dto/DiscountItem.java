package com.four_person.discount.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DiscountItem {

    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    private Long categoryId;

    /** 商品所属店铺，商家活动/商家券按店铺圈定范围 */
    private Long shopId;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.00", message = "商品价格不能为负数")
    private BigDecimal price;

    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量必须大于 0")
    private Integer quantity;
}
