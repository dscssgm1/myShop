package com.four_person.system.infrastructure.security;

import com.four_person.system.domain.model.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。使用 HS256，密钥来自 {@link JwtProperties}。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_GROUP = "group";
    private static final String CLAIM_SHOP_ID = "shopId";
    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_COURIER_ID = "courierId";
    private static final String CLAIM_TYP = "typ";
    private static final String TYP_DEVICE_VERIFY = "deviceVerify";
    private static final long DEVICE_VERIFY_TTL_SECONDS = 600;

    private final JwtProperties properties;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("myshop.security.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String generate(SysUser user, String groupCode) {
        return generate(user, groupCode, UUID.randomUUID().toString());
    }

    /**
     * 签发令牌并绑定会话 ID（jti）。
     * 会话属性存 Redis，网关按 jti 读取；因此令牌本身只作为「会话凭证」。
     */
    public String generate(SysUser user, String groupCode, String sessionId) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(properties.getExpirationSeconds());
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .id(sessionId)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_NICKNAME, user.getNickname())
                .claim(CLAIM_ROLE, user.getRoleCode())
                .claim(CLAIM_GROUP, groupCode)
                .claim(CLAIM_SHOP_ID, user.getShopId())
                .claim(CLAIM_COMPANY_ID, user.getCompanyId())
                .claim(CLAIM_COURIER_ID, user.getCourierId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }

    /**
     * 短时效「身份验证凭证」：任一邮箱验证码校验通过后签发，
     * 未信任设备修改密码/邮箱、标记信任设备时需出示。
     */
    public String generateDeviceToken(Long userId) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(DEVICE_VERIFY_TTL_SECONDS);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYP, TYP_DEVICE_VERIFY)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    /** 校验身份验证凭证，返回归属用户 id；无效或已过期返回 null */
    public Long parseDeviceTokenUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = parse(token);
            if (!TYP_DEVICE_VERIFY.equals(claims.get(CLAIM_TYP, String.class))) {
                return null;
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public static Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    /** 令牌对应的会话 ID（jti） */
    public static String sessionIdOf(Claims claims) {
        return claims.getId();
    }

    public static String roleOf(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public static String groupOf(Claims claims) {
        return claims.get(CLAIM_GROUP, String.class);
    }
}
