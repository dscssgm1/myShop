package com.four_person.system.infrastructure.security;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 读取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static SecurityUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return user;
        }
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}
