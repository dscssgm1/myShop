package com.four_person.discount.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CouponReleaseRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
