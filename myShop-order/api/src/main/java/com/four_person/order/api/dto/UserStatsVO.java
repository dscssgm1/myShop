package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserStatsVO {

    private Integer orderCount;

    private BigDecimal totalSpent;

    private LocalDateTime lastOrderAt;
}
