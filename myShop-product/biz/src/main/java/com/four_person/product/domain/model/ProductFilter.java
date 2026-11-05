package com.four_person.product.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品查询条件（领域层查询对象，避免依赖接口层 DTO）。
 */
public record ProductFilter(String keyword,
                            Long categoryId,
                            Long subCategoryId,
                            Long brandId,
                            Long shopId,
                            BigDecimal minPrice,
                            BigDecimal maxPrice,
                            String tag,
                            String sort,
                            Integer status,
                            String auditStatus,
                            boolean pendingEdit,
                            boolean needsReview,
                            boolean includeAll,
                            List<Long> excludeIds,
                            int page,
                            int size) {
}
