package com.four_person.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductVO {

    private Long id;

    private String name;

    private Long brandId;

    private String brandName;

    private Long categoryId;

    private String categoryName;

    /** 多类目 */
    private List<Long> categoryIds;

    private List<String> categoryNames;

    private Long shopId;

    private String shopName;

    private BigDecimal price;

    private BigDecimal originPrice;

    private BigDecimal discountPrice;

    private Integer stock;

    private Integer sales;

    private BigDecimal rating;

    private String emoji;

    private String color;

    private List<String> tags;

    private String description;

    /** 规格定义 */
    private List<SpecGroupVO> specs;

    /** SKU 列表 */
    private List<SkuVO> skus;

    private Integer status;

    private String auditStatus;

    private String offReason;

    @Data
    public static class SpecGroupVO {
        private String name;
        private List<String> options;
    }

    @Data
    public static class SkuVO {
        private Long id;
        private Map<String, String> specs;
        private BigDecimal price;
        private BigDecimal originPrice;
        private Integer stock;
        private Integer sales;
    }
}
