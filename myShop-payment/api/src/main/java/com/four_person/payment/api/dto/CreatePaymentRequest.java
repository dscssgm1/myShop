package com.four_person.payment.api.dto;

import lombok.Data;

/**
 * 创建支付单：普通订单传 orderNo；跨店合单整组支付传 groupNo（二选一）。
 */
@Data
public class CreatePaymentRequest {

    /** 普通订单号（与 groupNo 二选一） */
    private String orderNo;

    /** 跨店合单订单组号（与 orderNo 二选一，一次付清整组） */
    private String groupNo;

    private String channel;
}
