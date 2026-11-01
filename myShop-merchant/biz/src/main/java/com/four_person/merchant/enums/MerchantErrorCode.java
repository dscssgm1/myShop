package com.four_person.merchant.enums;

import com.four_person.common.exception.ErrorCode;

public enum MerchantErrorCode implements ErrorCode {

    SHOP_NOT_FOUND(40401, "店铺不存在"),
    SHOP_DISABLED(40301, "店铺已被禁用"),
    STAFF_NOT_FOUND(40402, "员工不存在"),
    APPLY_PENDING(40901, "已有待审核的入驻申请"),
    SHOP_NOT_BOUND(40302, "当前账号未绑定店铺");

    private final int code;
    private final String message;

    MerchantErrorCode(int code, String message) {
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
