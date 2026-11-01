package com.four_person.order.enums;

import com.four_person.common.exception.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    CART_ITEM_NOT_FOUND(40405, "购物车商品不存在"),
    CART_EMPTY(40009, "购物车为空"),
    ORDER_NOT_FOUND(40406, "订单不存在"),
    ORDER_STATUS_INVALID(40010, "订单状态不允许该操作"),
    ORDER_AMOUNT_INVALID(40011, "订单金额异常"),
    PRODUCT_NOT_FOUND(40407, "商品不存在"),
    PRODUCT_OFF_SHELF(40012, "商品已下架"),
    STOCK_INSUFFICIENT(40013, "商品库存不足"),
    ORDER_FORBIDDEN(40301, "无权操作该订单");

    private final int code;
    private final String message;

    OrderErrorCode(int code, String message) {
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
