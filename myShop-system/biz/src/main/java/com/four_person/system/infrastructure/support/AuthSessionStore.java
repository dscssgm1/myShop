package com.four_person.system.infrastructure.support;

import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.infrastructure.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录会话存储（Redis）。
 * <p>
 * 会话 key：{@code myshop:auth:session:{jti}}（Hash，TTL 与 JWT 一致）<br>
 * 用户索引：{@code myshop:auth:user:{userId}:sessions}（Set，存该用户所有 jti）
 * <p>
 * 网关每次请求按 jti 读取会话并注入身份头，所以修改角色、换绑店铺等操作
 * 只需更新会话 Hash，即可对后续请求立即生效。
 */
@Component
@RequiredArgsConstructor
public class AuthSessionStore {

    private static final String SESSION_PREFIX = "myshop:auth:session:";
    private static final String USER_INDEX_PREFIX = "myshop:auth:user:";
    private static final String INDEX_SUFFIX = ":sessions";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    /** 登录成功后创建会话，记录设备与 IP，供「登录设备」列表展示；perms 为角色勾选的权限码（逗号分隔） */
    public void create(String sessionId, SysUser user, String groupCode, String perms) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("userId", String.valueOf(user.getId()));
        values.put("username", nullToEmpty(user.getUsername()));
        values.put("nickname", nullToEmpty(user.getNickname()));
        values.put("role", nullToEmpty(user.getRoleCode()));
        values.put("group", nullToEmpty(groupCode));
        values.put("perms", nullToEmpty(perms));
        values.put("shopId", nullToEmpty(user.getShopId()));
        values.put("companyId", nullToEmpty(user.getCompanyId()));
        values.put("courierId", nullToEmpty(user.getCourierId()));
        values.put("ip", nullToEmpty(ClientIpUtils.currentIp()));
        values.put("deviceId", nullToEmpty(DeviceUtils.currentDeviceId()));
        values.put("deviceName", nullToEmpty(DeviceUtils.currentDeviceName()));
        values.put("loginAt", LocalDateTime.now().toString());

        Duration ttl = Duration.ofSeconds(jwtProperties.getExpirationSeconds());
        String key = sessionKey(sessionId);
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttl);

        String indexKey = indexKey(user.getId());
        redisTemplate.opsForSet().add(indexKey, sessionId);
        redisTemplate.expire(indexKey, ttl);
    }

    /** 当前用户所有在线设备（顺带清理已过期的索引项） */
    public List<AuthDtos.SessionVO> list(Long userId, String currentSessionId) {
        String indexKey = indexKey(userId);
        Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        List<AuthDtos.SessionVO> result = new ArrayList<>(sessionIds.size());
        for (String sessionId : sessionIds) {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(sessionKey(sessionId));
            if (values == null || values.isEmpty()) {
                redisTemplate.opsForSet().remove(indexKey, sessionId);
                continue;
            }
            result.add(toVO(sessionId, values, currentSessionId));
        }
        result.sort(Comparator.comparing(AuthDtos.SessionVO::getLoginAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /** 单点下线：只允许删除属于该用户的会话 */
    public boolean delete(Long userId, String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        String key = sessionKey(sessionId);
        Object owner = redisTemplate.opsForHash().get(key, "userId");
        if (owner == null || !String.valueOf(userId).equals(String.valueOf(owner))) {
            return false;
        }
        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(indexKey(userId), sessionId);
        return true;
    }

    /** 下线该用户全部设备 */
    public void deleteAll(Long userId) {
        deleteAllExcept(userId, null);
    }

    /** 下线除指定会话外的所有设备（修改密码时保留当前设备） */
    public void deleteAllExcept(Long userId, String keepSessionId) {
        String indexKey = indexKey(userId);
        Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        for (String sessionId : sessionIds) {
            if (StringUtils.hasText(keepSessionId) && keepSessionId.equals(sessionId)) {
                continue;
            }
            redisTemplate.delete(sessionKey(sessionId));
            redisTemplate.opsForSet().remove(indexKey, sessionId);
        }
    }

    /** 用户属性变更后刷新所有在线会话，让网关下一次请求就注入新值 */
    public void updateAttributes(Long userId, Map<String, String> attributes) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey(userId));
        if (sessionIds == null || sessionIds.isEmpty() || attributes.isEmpty()) {
            return;
        }
        for (String sessionId : sessionIds) {
            String key = sessionKey(sessionId);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.opsForHash().putAll(key, attributes);
            }
        }
    }

    private AuthDtos.SessionVO toVO(String sessionId, Map<Object, Object> values, String currentSessionId) {
        AuthDtos.SessionVO vo = new AuthDtos.SessionVO();
        vo.setSessionId(sessionId);
        vo.setUserId(parseLong(values.get("userId")));
        vo.setUsername(stringValue(values.get("username")));
        vo.setDeviceId(stringValue(values.get("deviceId")));
        vo.setDeviceName(stringValue(values.get("deviceName")));
        vo.setIp(stringValue(values.get("ip")));
        vo.setLoginAt(stringValue(values.get("loginAt")));
        vo.setCurrent(sessionId.equals(currentSessionId));
        return vo;
    }

    private String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String indexKey(Long userId) {
        return USER_INDEX_PREFIX + userId + INDEX_SUFFIX;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long parseLong(Object value) {
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
