package com.four_person.product.domain.model;

import java.math.BigDecimal;

/**
 * 商品查询条件（领域层查询对象，避免依赖接口层 DTO）。
 */
public record ProductFilter(String keyword,
                            Long categoryId,
                            BigDecimal minPrice,
                            BigDecimal maxPrice,
                            String tag,
                            String sort,
                            int page,
                            int size) {
}
