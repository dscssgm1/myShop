package com.four_person.common.exception;

/**
 * 错误码契约，各业务模块可自行定义枚举实现。
 */
public interface ErrorCode {

    int getCode();

    String getMessage();
}
