package com.four_person.order.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.order.api.dto.OrderGroupInternalDTO;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.OrderInternalDtos;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.dto.UserStatsVO;
import com.four_person.order.application.AfterSaleApplicationService;
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
 * 供支付服务、折扣服务和物流服务调用的内部接口。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderApplicationService orderApplicationService;
    private final AfterSaleApplicationService afterSaleApplicationService;

    @GetMapping("/orders/{orderNo}")
    public Result<OrderInternalDTO> get(@PathVariable String orderNo) {
        return Result.success(orderApplicationService.getInternal(orderNo));
    }

    @PostMapping("/orders/{orderNo}/paid")
    public Result<Void> paid(@PathVariable String orderNo, @Valid @RequestBody PaidRequest request) {
        orderApplicationService.markPaid(orderNo, request);
        return Result.success();
    }

    // ==================== 订单组合单支付 ====================

    /** 查询订单组信息（支付服务创建合单支付单时校验） */
    @GetMapping("/order-groups/{groupNo}")
    public Result<OrderGroupInternalDTO> getGroup(@PathVariable String groupNo) {
        return Result.success(orderApplicationService.getGroupInternal(groupNo));
    }

    /** 合单支付成功回调：组内待付款子订单整组置为已支付 */
    @PostMapping("/order-groups/{groupNo}/paid")
    public Result<Void> groupPaid(@PathVariable String groupNo, @Valid @RequestBody PaidRequest request) {
        orderApplicationService.markPaidGroup(groupNo, request);
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

    @GetMapping("/users/{userId}/unpaid-recovery")
    public Result<Boolean> hasUnpaidRecovery(@PathVariable Long userId) {
        return Result.success(orderApplicationService.hasUnpaidRecovery(userId));
    }

    // ==================== 物流签收回调 ====================

    /** 正向运单签收：记录签收时间，签收满 N 天自动确认收货 */
    @PostMapping("/orders/shipment-signed")
    public Result<Void> shipmentSigned(@Valid @RequestBody OrderInternalDtos.ShipmentSignedRequest request) {
        orderApplicationService.onShipmentSigned(request);
        return Result.success();
    }

    /** 售后退货运单签收：通知买家商家已收到退货 */
    @PostMapping("/orders/return-shipment-signed")
    public Result<Void> returnShipmentSigned(@Valid @RequestBody OrderInternalDtos.ReturnSignedRequest request) {
        afterSaleApplicationService.onReturnSigned(request);
        return Result.success();
    }
}
