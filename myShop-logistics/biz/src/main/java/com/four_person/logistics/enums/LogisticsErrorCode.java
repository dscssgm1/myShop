package com.four_person.logistics.enums;

import com.four_person.common.exception.ErrorCode;

public enum LogisticsErrorCode implements ErrorCode {

    COMPANY_NOT_FOUND(40401, "物流公司不存在"),
    COMPANY_NOT_ACTIVE(40001, "物流公司尚未入驻"),
    COURIER_NOT_FOUND(40402, "快递员不存在"),
    SHIPMENT_NOT_FOUND(40403, "运单不存在"),
    ILLEGAL_STATUS(40002, "运单状态不允许该操作");

    private final int code;
    private final String message;

    LogisticsErrorCode(int code, String message) {
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
