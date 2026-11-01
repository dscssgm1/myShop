package com.four_person.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.Result;
import com.four_person.common.exception.CommonErrorCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 全局 Token 鉴权：
 * <ul>
 *     <li>解析 {@code Authorization: Bearer <token>}，校验签名与有效期；</li>
 *     <li>把身份写入 {@code X-User-*} 请求头，下游服务直接读取；</li>
 *     <li>白名单路径允许匿名访问，其余未携带有效令牌一律 401。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-User-Username";
    private static final String HEADER_ROLE = "X-User-Role";
    private static final String HEADER_GROUP = "X-User-Group";
    private static final String HEADER_SHOP_ID = "X-User-Shop-Id";
    private static final String HEADER_COMPANY_ID = "X-User-Company-Id";
    private static final String HEADER_COURIER_ID = "X-User-Courier-Id";

    private final JwtTokenProvider tokenProvider;
    private final GatewaySecurityProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER)) {
            try {
                Claims claims = tokenProvider.parse(authorization.substring(BEARER.length()).trim());
                return chain.filter(withIdentity(exchange, claims));
            } catch (Exception ex) {
                log.debug("JWT 校验失败: {}", ex.getMessage());
                return unauthorized(exchange, "登录已过期，请重新登录");
            }
        }

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        return unauthorized(exchange, "未登录或登录已过期");
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Claims claims) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_ROLE);
                    headers.remove(HEADER_GROUP);
                    headers.remove(HEADER_SHOP_ID);
                    headers.remove(HEADER_COMPANY_ID);
                    headers.remove(HEADER_COURIER_ID);
                    set(headers, HEADER_USER_ID, claims.getSubject());
                    set(headers, HEADER_USERNAME, claims.get("username", String.class));
                    set(headers, HEADER_ROLE, claims.get("role", String.class));
                    set(headers, HEADER_GROUP, claims.get("group", String.class));
                    set(headers, HEADER_SHOP_ID, claims.get("shopId", Long.class));
                    set(headers, HEADER_COMPANY_ID, claims.get("companyId", Long.class));
                    set(headers, HEADER_COURIER_ID, claims.get("courierId", Long.class));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private void set(HttpHeaders headers, String name, Object value) {
        if (value != null) {
            headers.set(name, String.valueOf(value));
        }
    }

    private boolean isWhitelisted(String path) {
        return properties.getWhitelist().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.error(CommonErrorCode.UNAUTHORIZED.getCode(), message));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
