package com.four_person.system.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证通过后放入 SecurityContext 的当前用户。
 */
@Data
@AllArgsConstructor
public class SecurityUser {

    private Long id;
    private String username;
    private String nickname;
    private String roleCode;
    private String groupCode;
    private Long shopId;
    private Long companyId;
    private Long courierId;
}
