package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单组内部信息，供支付服务创建/支付合单支付单时校验。
 */
@Data
public class OrderGroupInternalDTO {

    private String groupNo;

    private Long userId;

    /** UNPAID / PAID / CANCELLED / CLOSED */
    private String status;

    private Integer shopCount;

    /** 组应付总额（一次付清的金额） */
    private BigDecimal payAmount;

    /** 券核销时使用的订单号（第一张子单） */
    private String couponUseOrderNo;
}
