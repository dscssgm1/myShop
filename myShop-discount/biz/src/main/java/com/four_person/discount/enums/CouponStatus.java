package com.four_person.discount.enums;

public enum CouponStatus {

    UNUSED("未使用"),
    USED("已使用"),
    EXPIRED("已过期");

    private final String text;

    CouponStatus(String text) {
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
