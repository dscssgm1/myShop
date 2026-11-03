package com.four_person.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 按订单号发起退款（由订单服务在取消订单 / 售后退款时调用）。
 */
@Data
public class RefundRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    private String reason;
}
