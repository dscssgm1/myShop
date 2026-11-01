package com.four_person.common.web;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;

import java.util.Arrays;

/**
 * 基于网关透传的角色/所属端做接口级校验。
 * 用于网关无法按路径区分的细粒度权限（例如同一控制器内的管理端动作）。
 */
public final class AccessGuard {

    private AccessGuard() {
    }

    /** 要求属于指定端，例如 PLATFORM / MERCHANT / LOGISTICS */
    public static void requireGroup(String... groups) {
        String current = UserContext.getGroup();
        if (current == null || Arrays.stream(groups).noneMatch(current::equals)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 要求具体角色，例如 admin / merchant / logistics */
    public static void requireRole(String... roles) {
        String current = UserContext.getRole();
        if (current == null || Arrays.stream(roles).noneMatch(current::equals)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
