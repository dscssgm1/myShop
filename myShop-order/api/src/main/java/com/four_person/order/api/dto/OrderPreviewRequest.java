package com.four_person.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderPreviewRequest {

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<OrderItemRequest> items;

    private Long couponId;
}
