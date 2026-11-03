package com.four_person.payment.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentVO {

    private String paymentNo;

    private String orderNo;

    private BigDecimal amount;

    private String channel;

    private String channelText;

    private String status;

    private String statusText;

    private String tradeNo;

    private LocalDateTime paidAt;

    private LocalDateTime refundedAt;

    private String refundReason;

    private LocalDateTime createdAt;
}
