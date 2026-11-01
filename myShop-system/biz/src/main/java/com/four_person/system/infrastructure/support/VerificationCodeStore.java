package com.four_person.system.infrastructure.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 邮箱验证码存储（Redis）：天然支持 TTL 过期与一次性删除。
 * key 结构：{@code myshop:code:{purpose}:{email}}
 */
@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    private static final String KEY_PREFIX = "myshop:code:";

    private final StringRedisTemplate redisTemplate;

    public void save(String email, String purpose, String code, Duration ttl) {
        redisTemplate.opsForValue().set(key(email, purpose), code, ttl);
    }

    public Optional<String> findValid(String email, String purpose) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(email, purpose)));
    }

    /** 验证通过后作废（一次性使用） */
    public void invalidate(String email, String purpose) {
        redisTemplate.delete(key(email, purpose));
    }

    private String key(String email, String purpose) {
        return KEY_PREFIX + purpose + ":" + email;
    }
}
