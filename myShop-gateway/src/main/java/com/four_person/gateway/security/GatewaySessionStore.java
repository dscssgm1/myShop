package com.four_person.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关侧登录会话读取器。
 * <p>
 * 会话由 myShop-system 在登录成功时写入
 * {@code myshop:auth:session:{jti}}（Hash，TTL 与 JWT 一致）。
 * 网关每次请求按令牌的 jti 读取会话，因此登出、单点下线、角色变更、
 * 禁用账号等操作都能对下一次请求立即生效。
 */
@Component
@RequiredArgsConstructor
public class GatewaySessionStore {

    private static final String SESSION_PREFIX = "myshop:auth:session:";

    private final ReactiveStringRedisTemplate redisTemplate;

    /** 读取会话属性；会话不存在（已登出 / 被踢下线 / 已过期）时返回空 Mono */
    public Mono<Map<String, String>> find(String sessionId) {
        return redisTemplate.<String, String>opsForHash()
                .entries(SESSION_PREFIX + sessionId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .filter(values -> !values.isEmpty());
    }
}
