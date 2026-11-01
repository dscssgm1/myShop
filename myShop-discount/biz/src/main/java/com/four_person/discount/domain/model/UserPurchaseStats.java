package com.four_person.discount.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户消费统计只读模型。
 */
public record UserPurchaseStats(int orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt) {

    public static UserPurchaseStats empty() {
        return new UserPurchaseStats(0, BigDecimal.ZERO, null);
    }
}
