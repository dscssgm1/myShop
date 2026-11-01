package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartVO {

    private List<CartItemVO> items;

    private Integer totalQuantity;

    private BigDecimal totalAmount;
}
