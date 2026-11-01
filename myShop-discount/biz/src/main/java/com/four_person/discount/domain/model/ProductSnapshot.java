package com.four_person.discount.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐/推送使用的商品只读模型。
 */
public record ProductSnapshot(Long id,
                              String name,
                              Money price,
                              Long categoryId,
                              String categoryName,
                              Long shopId,
                              String shopName,
                              String emoji,
                              String color,
                              List<String> tags,
                              Integer sales,
                              BigDecimal rating) {

    public ProductSnapshot {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
