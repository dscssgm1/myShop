package com.four_person.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaidRequest {

    @NotBlank(message = "支付单号不能为空")
    private String paymentNo;

    private String channel;

    private LocalDateTime paidAt;
}
