package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderInternalDTO {

    private String orderNo;

    private Long userId;

    private String status;

    /** NORMAL 普通订单 / RECOVERY 售后补款订单 */
    private String bizType;

    /** 跨店合单订单组号；非空表示合单子订单，须整组支付 */
    private String groupNo;

    private BigDecimal payAmount;
}
