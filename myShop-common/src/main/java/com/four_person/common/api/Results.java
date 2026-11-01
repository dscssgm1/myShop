package com.four_person.common.api;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;

/**
 * 解包跨服务调用返回的 {@link Result}。
 */
public final class Results {

    private Results() {
    }

    public static <T> T data(Result<T> result) {
        if (result == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "远程服务返回为空");
        }
        if (result.getCode() != CommonErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
