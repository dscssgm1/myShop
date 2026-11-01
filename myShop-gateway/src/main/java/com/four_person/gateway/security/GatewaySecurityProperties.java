package com.four_person.gateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置：JWT 密钥 + 免登录路径前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "myshop.security")
public class GatewaySecurityProperties {

    private Jwt jwt = new Jwt();

    /** 免登录访问的路径前缀，例如 /api/products */
    private List<String> whitelist = new ArrayList<>();

    @Data
    public static class Jwt {
        private String secret = "myshop-default-secret-please-change-in-production-2026";
    }
}
