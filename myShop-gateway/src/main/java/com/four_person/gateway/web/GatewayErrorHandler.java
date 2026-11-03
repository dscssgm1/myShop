package com.four_person.gateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 网关全局异常处理：
 * 下游服务掉线（无可用实例、连接被拒绝、响应超时）时，
 * 返回统一的 JSON 提示，并带上具体服务名称，前端可直接展示
 * 「订单服务异常，请联系客服」这类文案，而不是笼统的网络错误。
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    /** 路由 ID → 对外展示的服务名称 */
    private static final Map<String, String> SERVICE_NAMES = Map.ofEntries(
            Map.entry("myshop-system", "用户服务"),
            Map.entry("myshop-product", "商品服务"),
            Map.entry("myshop-order", "订单服务"),
            Map.entry("myshop-payment", "支付服务"),
            Map.entry("myshop-discount", "营销服务"),
            Map.entry("myshop-merchant", "商家服务"),
            Map.entry("myshop-logistics", "物流服务"),
            Map.entry("myshop-message", "消息服务"),
            Map.entry("myshop-platform", "平台服务"),
            Map.entry("myshop-review", "评价服务"),
            Map.entry("myshop-file", "文件服务")
    );

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? null : route.getId();
        String serviceName = routeId == null ? null : SERVICE_NAMES.get(routeId);

        String message = serviceName == null
                ? "服务暂不可用，请稍后重试或联系客服"
                : serviceName + "异常，请联系客服";
        log.warn("网关转发失败: path={}, route={}, cause={}", path, routeId, ex.toString());

        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Result.error(50300, message));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception writeError) {
            log.error("写入网关错误响应失败", writeError);
            return response.setComplete();
        }
    }
}
