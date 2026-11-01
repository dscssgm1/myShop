package com.four_person.system.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置：密钥、有效期与签发者。
 * 生产环境务必通过环境变量覆盖 {@code MYSHOP_JWT_SECRET}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "myshop.security.jwt")
public class JwtProperties {

    private String secret = "myshop-default-secret-please-change-in-production-2026";

    /** 有效期（秒），默认 2 小时 */
    private long expirationSeconds = 7200;

    private String issuer = "myshop";
}
