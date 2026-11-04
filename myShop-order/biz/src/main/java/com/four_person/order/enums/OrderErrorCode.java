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
    CANNOT_BUY_OWN_PRODUCT(40024, "商家不能购买自己店铺的商品"),
    AFTER_SALE_NOT_FOUND(40410, "售后单不存在"),
    AFTER_SALE_STATUS_INVALID(40026, "售后单状态不允许该操作"),
    AFTER_SALE_EXISTS(40027, "该订单已有进行中的售后申请"),
    UNPAID_RECOVERY_EXISTS(40028, "您有未支付的补款订单，请先完成支付后再进行其他购买"),
    APPOINTMENT_INVALID(40029, "上门取件时间需在两天以内"),
    REFUND_FAILED(40030, "退款失败，请稍后重试或联系客服"),
    RETURN_SHIPMENT_FAILED(40031, "生成退货运单失败，请稍后重试"),
    SHIPMENT_FAILED(40032, "创建物流运单失败，请稍后重试"),
    ORDER_GROUP_NOT_FOUND(40411, "订单组不存在"),
    ORDER_GROUP_STATUS_INVALID(40033, "订单组当前状态不允许该操作"),
    ORDER_FORBIDDEN(40301, "无权操作该订单"),
    AFTER_SALE_FORBIDDEN(40303, "无权操作该售后单");

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
