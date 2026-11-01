package com.four_person.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductQuery {

    private String keyword;

    /** 大类 */
    private Long categoryId;

    /** 小类 */
    private Long subCategoryId;

    private Long brandId;

    /** 店铺 ID（店铺页） */
    private Long shopId;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private String tag;

    /** default / sales / priceAsc / priceDesc */
    private String sort = "default";

    private Integer page = 1;

    private Integer size = 8;
}
