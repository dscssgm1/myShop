package com.four_person.order.api.enums;

/**
 * 售后类型。
 */
public enum AfterSaleType {

    REFUND_ONLY("仅退款"),
    RETURN_REFUND("退货退款");

    private final String text;

    AfterSaleType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static String textOf(String type) {
        try {
            return valueOf(type).getText();
        } catch (Exception ex) {
            return type;
        }
    }
}
