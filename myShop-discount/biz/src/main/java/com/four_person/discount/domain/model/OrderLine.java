package com.four_person.discount.domain.model;

import java.math.BigDecimal;

/**
 * 算价输入行。
 */
public record OrderLine(Long productId, Long categoryId, Long shopId, Money unitPrice, int quantity) {

    public Money subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
