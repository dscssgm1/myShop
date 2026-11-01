package com.four_person.order.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.dto.UserStatsVO;
import com.four_person.order.application.OrderApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供支付服务和折扣服务调用的内部接口。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderApplicationService orderApplicationService;

    @GetMapping("/orders/{orderNo}")
    public Result<OrderInternalDTO> get(@PathVariable String orderNo) {
        return Result.success(orderApplicationService.getInternal(orderNo));
    }

    @PostMapping("/orders/{orderNo}/paid")
    public Result<Void> paid(@PathVariable String orderNo, @Valid @RequestBody PaidRequest request) {
        orderApplicationService.markPaid(orderNo, request);
        return Result.success();
    }

    @GetMapping("/users/{userId}/purchased-products")
    public Result<List<Long>> purchasedProducts(@PathVariable Long userId) {
        return Result.success(orderApplicationService.purchasedProductIds(userId));
    }

    @GetMapping("/users/{userId}/stats")
    public Result<UserStatsVO> stats(@PathVariable Long userId) {
        return Result.success(orderApplicationService.userStats(userId));
    }
}
