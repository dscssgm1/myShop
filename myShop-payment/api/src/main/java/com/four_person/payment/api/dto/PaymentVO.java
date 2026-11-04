package com.four_person.payment.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentVO {

    private String paymentNo;

    private String orderNo;

    /** 跨店合单订单组号；非空表示整组一次付清的支付单 */
    private String groupNo;

    private BigDecimal amount;

    /** 已累计退款金额（合单部分退款） */
    private BigDecimal refundedAmount;

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
