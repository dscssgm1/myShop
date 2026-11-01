package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderInternalDTO {

    private String orderNo;

    private Long userId;

    private String status;

    private BigDecimal payAmount;
}
