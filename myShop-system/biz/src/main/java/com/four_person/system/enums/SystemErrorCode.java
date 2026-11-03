package com.four_person.system.enums;

import com.four_person.common.exception.ErrorCode;

public enum SystemErrorCode implements ErrorCode {

    USERNAME_EXISTS(40901, "用户名已存在"),
    EMAIL_EXISTS(40902, "该邮箱已被使用"),
    USER_NOT_FOUND(40401, "用户不存在"),
    PASSWORD_ERROR(40001, "用户名或密码错误"),
    USER_DISABLED(40301, "账号已被禁用"),
    ROLE_NOT_FOUND(40402, "角色不存在"),
    ROLE_CODE_EXISTS(40903, "角色编码已存在"),
    ROLE_IN_USE(40904, "该角色下仍有用户，无法删除"),
    ADDRESS_NOT_FOUND(40403, "收货地址不存在"),
    BANK_CARD_NOT_FOUND(40404, "银行卡不存在"),
    EMAIL_CODE_INVALID(40002, "验证码错误或已过期"),
    OLD_PASSWORD_ERROR(40003, "当前密码错误"),
    POINTS_INSUFFICIENT(40004, "积分不足"),
    SEND_TOO_FREQUENT(42901, "发送太频繁，请稍后再试"),
    SEND_LIMIT_EXCEEDED(42902, "今日发送验证码次数已达上限"),
    IDENTITY_VERIFY_REQUIRED(40005, "当前设备未被信任，请先完成邮箱身份验证"),
    DEVICE_VERIFY_INVALID(40006, "身份验证已过期，请重新验证");

    private final int code;
    private final String message;

    SystemErrorCode(int code, String message) {
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
