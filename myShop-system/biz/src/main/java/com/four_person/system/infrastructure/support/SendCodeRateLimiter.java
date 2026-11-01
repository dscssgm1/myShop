package com.four_person.system.infrastructure.support;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.enums.SystemErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 发送验证码限流：
 * <ul>
 *     <li>同一邮箱 60 秒内只能发送一次（SETNX 原子占位）；</li>
 *     <li>同一 IP 每天最多发送 10 次（INCR 计数）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SendCodeRateLimiter {

    private static final String EMAIL_KEY_PREFIX = "myshop:code:limit:email:";
    private static final String IP_KEY_PREFIX = "myshop:code:limit:ip:";
    private static final long EMAIL_INTERVAL_SECONDS = 60;
    private static final long IP_DAILY_LIMIT = 10;

    private final StringRedisTemplate redisTemplate;

    public void check(String email, String ip) {
        checkEmail(email);
        checkIp(ip);
    }

    private void checkEmail(String email) {
        String key = EMAIL_KEY_PREFIX + email;
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(EMAIL_INTERVAL_SECONDS));
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(SystemErrorCode.SEND_TOO_FREQUENT,
                    "发送太频繁，请 " + EMAIL_INTERVAL_SECONDS + " 秒后再试");
        }
    }

    private void checkIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        String key = IP_KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 首次计数时设置一天过期
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > IP_DAILY_LIMIT) {
            throw new BusinessException(SystemErrorCode.SEND_LIMIT_EXCEEDED);
        }
    }
}
