package com.four_person.payment.enums;

import com.four_person.common.exception.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(40408, "订单不存在"),
    ORDER_NOT_PAYABLE(40014, "订单当前状态不可支付"),
    PAYMENT_NOT_FOUND(40409, "支付单不存在"),
    PAYMENT_STATUS_INVALID(40015, "支付单状态不允许该操作"),
    PAYMENT_FORBIDDEN(40302, "无权操作该支付单"),
    CHANNEL_INVALID(40016, "不支持的支付渠道"),
    UNPAID_RECOVERY_EXISTS(40017, "您有未支付的补款订单，请先完成支付后再进行其他购买"),
    PAY_PARAM_INVALID(40018, "订单号与订单组号必须二选一"),
    GROUP_NOT_PAYABLE(40034, "订单组当前状态不可支付");

    private final int code;
    private final String message;

    PaymentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
