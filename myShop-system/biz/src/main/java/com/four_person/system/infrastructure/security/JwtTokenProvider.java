package com.four_person.system.infrastructure.security;

import com.four_person.system.domain.model.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

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
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(properties.getExpirationSeconds());
        return Jwts.builder()
                .issuer(properties.getIssuer())
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

    public static Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static String roleOf(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public static String groupOf(Claims claims) {
        return claims.get(CLAIM_GROUP, String.class);
    }
}
