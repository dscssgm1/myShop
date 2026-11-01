package com.four_person.payment.enums;

import com.four_person.common.exception.BusinessException;

public enum PaymentChannel {

    ALIPAY("支付宝"),
    WECHAT("微信支付"),
    CARD("银行卡支付");

    private final String text;

    PaymentChannel(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static PaymentChannel of(String value) {
        if (value == null) {
            throw new BusinessException(PaymentErrorCode.CHANNEL_INVALID);
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(PaymentErrorCode.CHANNEL_INVALID);
        }
    }

    public static String textOf(String value) {
        try {
            return of(value).getText();
        } catch (Exception ex) {
            return value;
        }
    }
}
