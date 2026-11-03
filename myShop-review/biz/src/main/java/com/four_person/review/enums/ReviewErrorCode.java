package com.four_person.review.enums;

import com.four_person.common.exception.ErrorCode;

public enum ReviewErrorCode implements ErrorCode {

    REVIEW_NOT_PURCHASED(40302, "只有已完成购买的用户才能评价该商品"),
    REVIEW_WINDOW_EXPIRED(40019, "订单完成已超过 30 天，无法再评价"),
    REVIEW_ALREADY_DONE(40903, "该订单中的商品已评价"),
    REVIEW_PRODUCT_DONE(40904, "该商品已评价过，不能重复评价"),
    REVIEW_DAILY_LIMIT(40020, "今日评价次数已达上限（10 条），请明天再来"),
    REVIEW_CONTENT_TOO_SHORT(40021, "评价内容至少 5 个字"),
    REVIEW_SENSITIVE(40022, "评价内容包含敏感词，请修改后重试"),
    REVIEW_NOT_FOUND(40408, "评价不存在"),
    REVIEW_REPORT_DONE(40905, "该评价已被举报，正在等待平台处理"),
    REVIEW_FORBIDDEN(40303, "无权操作该评价"),
    REVIEW_STATUS_INVALID(40023, "当前评价状态不允许该操作");

    private final int code;
    private final String message;

    ReviewErrorCode(int code, String message) {
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
