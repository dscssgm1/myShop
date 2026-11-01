package com.four_person.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户消费统计（供折扣服务的 RFM 推送使用）。
 */
public record UserStats(int orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt) {
}
