package com.four_person.discount.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CouponUseRequest {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "订单号不能为空")
    private String orderNo;
}
