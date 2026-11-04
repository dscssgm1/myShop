package com.four_person.common.web;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;

import java.util.Arrays;
import java.util.Set;

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

    /**
     * 要求当前角色拥有任一权限码（角色管理「配置菜单」勾选的菜单/按钮 key）。
     * <p>权限集由网关从登录会话透传（X-User-Perms）；缺失视为无权限（fail-closed），
     * 角色菜单变更通过会话刷新即时生效。
     */
    public static void requirePerm(String... perms) {
        Set<String> own = UserContext.getPerms();
        if (own.isEmpty() || Arrays.stream(perms).noneMatch(own::contains)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
