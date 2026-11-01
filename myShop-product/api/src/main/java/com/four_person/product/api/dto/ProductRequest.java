package com.four_person.product.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductRequest {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    private Long brandId;

    @NotNull(message = "商品主分类不能为空")
    private Long categoryId;

    /** 多类目（含主分类与其他类目） */
    private List<Long> categoryIds;

    private Long shopId;

    private String shopName;

    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    private BigDecimal price;

    private BigDecimal originPrice;

    /** 促销价，空则无折扣 */
    private BigDecimal discountPrice;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    private String emoji;

    private String color;

    private List<String> tags;

    private String description;

    /** 商品规格定义 */
    private List<SpecGroupRequest> specs;

    /** 规格组合 SKU，空则无规格 */
    private List<SkuRequest> skus;

    /** 1 上架，0 下架，默认上架 */
    private Integer status;

    @Data
    public static class SpecGroupRequest {
        @NotBlank(message = "规格名称不能为空")
        private String name;
        private List<String> options;
    }

    @Data
    public static class SkuRequest {
        private Map<String, String> specs;
        @NotNull(message = "SKU 价格不能为空")
        @DecimalMin(value = "0.01", message = "SKU 价格必须大于 0")
        private BigDecimal price;
        private BigDecimal originPrice;
        @Min(value = 0, message = "库存不能为负数")
        private Integer stock = 0;
    }
}
