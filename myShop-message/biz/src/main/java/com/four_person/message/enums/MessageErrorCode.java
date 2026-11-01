package com.four_person.message.enums;

import com.four_person.common.exception.ErrorCode;

public enum MessageErrorCode implements ErrorCode {

    SESSION_NOT_FOUND(40401, "会话不存在"),
    SESSION_FORBIDDEN(40301, "无权访问该会话");

    private final int code;
    private final String message;

    MessageErrorCode(int code, String message) {
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
