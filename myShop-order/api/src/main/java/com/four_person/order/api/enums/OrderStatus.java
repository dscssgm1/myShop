package com.four_person.order.api.enums;

/**
 * 订单状态，name 用于持久化与接口传输，text 用于前端展示。
 */
public enum OrderStatus {

    UNPAID("待付款"),
    PAID("待发货"),
    SHIPPED("待收货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String text;

    OrderStatus(String text) {
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

    public static boolean isPaid(String status) {
        return PAID.name().equals(status) || SHIPPED.name().equals(status) || COMPLETED.name().equals(status);
    }
}
