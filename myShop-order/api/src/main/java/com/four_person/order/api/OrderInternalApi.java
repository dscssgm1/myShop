package com.four_person.order.api;

import com.four_person.common.api.Result;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.dto.UserStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 订单服务内部契约，供支付、折扣服务调用。
 */
@FeignClient(name = "myshop-order", url = "${myshop.clients.order-url:}")
public interface OrderInternalApi {

    @GetMapping("/internal/orders/{orderNo}")
    Result<OrderInternalDTO> getOrder(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/orders/{orderNo}/paid")
    Result<Void> paid(@PathVariable("orderNo") String orderNo, @RequestBody PaidRequest request);

    @GetMapping("/internal/users/{userId}/purchased-products")
    Result<List<Long>> purchasedProducts(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/{userId}/stats")
    Result<UserStatsVO> stats(@PathVariable("userId") Long userId);
}
