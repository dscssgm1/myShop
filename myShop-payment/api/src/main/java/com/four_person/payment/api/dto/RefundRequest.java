package com.four_person.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 按订单号发起退款（由订单服务在取消订单 / 售后退款时调用）。
 *
 * <p>跨店合单子订单共用一张组支付单：请求携带 groupNo + amount（子单分摊后的实付），
 * 支付服务按部分退款累计；普通订单不带 groupNo/amount，整单全额退款。</p>
 */
@Data
public class RefundRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 合单订单组号；非空时按组支付单做部分退款 */
    private String groupNo;

    /** 本次退款金额；空表示整单全额退款 */
    private BigDecimal amount;

    private String reason;
}
