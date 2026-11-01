package com.four_person.product.enums;

import com.four_person.common.exception.ErrorCode;

public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(40401, "商品不存在"),
    CATEGORY_NOT_FOUND(40402, "分类不存在"),
    PRODUCT_OFF_SHELF(40001, "商品已下架"),
    STOCK_INSUFFICIENT(40002, "商品库存不足");

    private final int code;
    private final String message;

    ProductErrorCode(int code, String message) {
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
