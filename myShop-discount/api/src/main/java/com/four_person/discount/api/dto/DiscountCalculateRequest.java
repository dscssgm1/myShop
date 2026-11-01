package com.four_person.discount.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DiscountCalculateRequest {

    /** 内部调用时由订单服务传入；为空时取请求头 X-User-Id */
    private Long userId;

    private Long couponId;

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<DiscountItem> items;
}
