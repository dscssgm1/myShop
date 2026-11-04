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

import java.util.Map;

/**
 * 全局 Token 鉴权：
 * <ul>
 *     <li>解析 {@code Authorization: Bearer <token>}，校验签名与有效期；</li>
 *     <li>按令牌 jti 从 Redis 读取登录会话，会话不存在（已登出 / 被下线 / 已过期）即视为未登录；</li>
 *     <li>身份属性取自会话而不是令牌，属性变更后下一次请求立即生效；</li>
 *     <li>把身份写入 {@code X-User-*} 请求头，并始终剥离客户端自带的身份头；</li>
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
    private static final String HEADER_PERMS = "X-User-Perms";
    private static final String HEADER_SHOP_ID = "X-User-Shop-Id";
    private static final String HEADER_COMPANY_ID = "X-User-Company-Id";
    private static final String HEADER_COURIER_ID = "X-User-Courier-Id";

    private final JwtTokenProvider tokenProvider;
    private final GatewaySessionStore sessionStore;
    private final GatewaySecurityProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER)) {
            return isWhitelisted(path)
                    ? chain.filter(withoutIdentity(exchange))
                    : unauthorized(exchange, "未登录或登录已过期");
        }

        Claims claims;
        try {
            claims = tokenProvider.parse(authorization.substring(BEARER.length()).trim());
        } catch (Exception ex) {
            log.debug("JWT 校验失败: {}", ex.getMessage());
            // 公开接口允许匿名访问：令牌过期或损坏时按匿名放行，避免影响游客浏览
            return isWhitelisted(path)
                    ? chain.filter(withoutIdentity(exchange))
                    : unauthorized(exchange, "登录已过期，请重新登录");
        }

        String sessionId = claims.getId();
        if (!StringUtils.hasText(sessionId)) {
            return isWhitelisted(path)
                    ? chain.filter(withoutIdentity(exchange))
                    : unauthorized(exchange, "登录已过期，请重新登录");
        }

        return sessionStore.find(sessionId)
                .map(session -> Resolved.authenticated(withIdentity(exchange, session)))
                .defaultIfEmpty(Resolved.anonymous(withoutIdentity(exchange)))
                .onErrorResume(ex -> {
                    log.error("读取登录会话失败（Redis 不可用）", ex);
                    return Mono.just(Resolved.unavailable(exchange));
                })
                .flatMap(resolved -> {
                    if (resolved.authenticated()) {
                        return chain.filter(resolved.exchange());
                    }
                    if (resolved.unavailable()) {
                        return fail(exchange, HttpStatus.SERVICE_UNAVAILABLE, "认证服务暂不可用，请稍后重试");
                    }
                    if (isWhitelisted(path)) {
                        return chain.filter(resolved.exchange());
                    }
                    return unauthorized(exchange, "登录已失效，请重新登录");
                });
    }

    /** 用会话中的身份属性覆盖请求头；客户端自带的 {@code X-User-*} 一律先剥离 */
    private ServerWebExchange withIdentity(ServerWebExchange exchange, Map<String, String> session) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    stripIdentity(headers);
                    set(headers, HEADER_USER_ID, session.get("userId"));
                    set(headers, HEADER_USERNAME, session.get("username"));
                    set(headers, HEADER_ROLE, session.get("role"));
                    set(headers, HEADER_GROUP, session.get("group"));
                    set(headers, HEADER_PERMS, session.get("perms"));
                    set(headers, HEADER_SHOP_ID, session.get("shopId"));
                    set(headers, HEADER_COMPANY_ID, session.get("companyId"));
                    set(headers, HEADER_COURIER_ID, session.get("courierId"));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    /** 匿名访问：剥离客户端伪造的身份头 */
    private ServerWebExchange withoutIdentity(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(JwtAuthGlobalFilter::stripIdentity)
                .build();
        return exchange.mutate().request(request).build();
    }

    private static void stripIdentity(HttpHeaders headers) {
        headers.remove(HEADER_USER_ID);
        headers.remove(HEADER_USERNAME);
        headers.remove(HEADER_ROLE);
        headers.remove(HEADER_GROUP);
        headers.remove(HEADER_PERMS);
        headers.remove(HEADER_SHOP_ID);
        headers.remove(HEADER_COMPANY_ID);
        headers.remove(HEADER_COURIER_ID);
    }

    private static void set(HttpHeaders headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value);
        }
    }

    private boolean isWhitelisted(String path) {
        return properties.getWhitelist().stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return fail(exchange, HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED.getCode(), message);
    }

    private Mono<Void> fail(ServerWebExchange exchange, HttpStatus status, String message) {
        return fail(exchange, status, status.value(), message);
    }

    private Mono<Void> fail(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Result.error(code, message));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            log.error("写入错误响应失败", ex);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * 会话解析结果。
     * <p>不能用 {@code switchIfEmpty} 串联 {@code chain.filter(...)}：转发链路返回的
     * {@code Mono<Void>} 永远以 empty 结束，会导致请求被转发两次。
     */
    private record Resolved(ServerWebExchange exchange, boolean authenticated, boolean unavailable) {

        static Resolved authenticated(ServerWebExchange exchange) {
            return new Resolved(exchange, true, false);
        }

        static Resolved anonymous(ServerWebExchange exchange) {
            return new Resolved(exchange, false, false);
        }

        static Resolved unavailable(ServerWebExchange exchange) {
            return new Resolved(exchange, false, true);
        }
    }
}
