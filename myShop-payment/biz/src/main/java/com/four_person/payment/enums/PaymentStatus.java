package com.four_person.payment.enums;

public enum PaymentStatus {

    PENDING("待支付"),
    SUCCESS("支付成功"),
    CLOSED("已关闭"),
    REFUNDED("已退款");

    private final String text;

    PaymentStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static String textOf(String status) {
        try {
            return valueOf(status).getText();
        } catch (Exception ex) {
            return status;
        }
    }
}
