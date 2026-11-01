package com.four_person.discount.enums;

import com.four_person.common.exception.ErrorCode;

public enum DiscountErrorCode implements ErrorCode {

    PROMOTION_NOT_FOUND(40403, "促销活动不存在"),
    COUPON_NOT_FOUND(40404, "优惠券不存在"),
    COUPON_NOT_AVAILABLE(40003, "优惠券不可用"),
    COUPON_ALREADY_USED(40004, "优惠券已被使用"),
    COUPON_STOCK_EMPTY(40005, "优惠券已领完"),
    CLAIM_LIMIT_REACHED(40006, "已达到领取上限"),
    INVALID_RULE(40007, "促销规则配置不合法"),
    INVALID_SEGMENT(40008, "用户分层不合法");

    private final int code;
    private final String message;

    DiscountErrorCode(int code, String message) {
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
