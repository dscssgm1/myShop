package com.four_person.product.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    @NotNull(message = "商品分类不能为空")
    private Long categoryId;

    private Long shopId;

    private String shopName;

    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    private BigDecimal price;

    private BigDecimal originPrice;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    private String emoji;

    private String color;

    private List<String> tags;

    private String description;

    private List<String> specs;

    /** 1 上架，0 下架，默认上架 */
    private Integer status;
}
