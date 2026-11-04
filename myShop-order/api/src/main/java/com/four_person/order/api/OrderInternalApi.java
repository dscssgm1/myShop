package com.four_person.order.api;

import com.four_person.common.api.Result;
import com.four_person.order.api.dto.OrderGroupInternalDTO;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.OrderInternalDtos;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.dto.UserStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 订单服务内部契约，供支付、折扣、物流等服务调用。
 */
@FeignClient(name = "myshop-order", url = "${myshop.clients.order-url:}")
public interface OrderInternalApi {

    // ==================== 支付服务：支付联动 ====================

    @GetMapping("/internal/orders/{orderNo}")
    Result<OrderInternalDTO> getOrder(@PathVariable("orderNo") String orderNo);

    @PostMapping("/internal/orders/{orderNo}/paid")
    Result<Void> paid(@PathVariable("orderNo") String orderNo, @RequestBody PaidRequest request);

    // ==================== 支付服务：订单组合单支付 ====================

    /** 查询订单组（父订单）信息，用于创建合单支付单 */
    @GetMapping("/internal/order-groups/{groupNo}")
    Result<OrderGroupInternalDTO> getOrderGroup(@PathVariable("groupNo") String groupNo);

    /** 合单支付成功回调：组内全部待付款子订单置为已支付，订单组置为已支付 */
    @PostMapping("/internal/order-groups/{groupNo}/paid")
    Result<Void> groupPaid(@PathVariable("groupNo") String groupNo, @RequestBody PaidRequest request);

    @GetMapping("/internal/users/{userId}/unpaid-recovery")
    Result<Boolean> hasUnpaidRecovery(@PathVariable("userId") Long userId);

    // ==================== 折扣服务：用户购买查询 ====================

    @GetMapping("/internal/users/{userId}/purchased-products")
    Result<List<Long>> purchasedProducts(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/{userId}/stats")
    Result<UserStatsVO> stats(@PathVariable("userId") Long userId);

    // ==================== 物流服务：签收回调 ====================

    /** 正向运单签收：记录签收时间，签收满 N 天自动确认收货 */
    @PostMapping("/internal/orders/shipment-signed")
    Result<Void> shipmentSigned(@RequestBody OrderInternalDtos.ShipmentSignedRequest request);

    /** 售后退货运单签收：通知买家商家已收到退货 */
    @PostMapping("/internal/orders/return-shipment-signed")
    Result<Void> returnShipmentSigned(@RequestBody OrderInternalDtos.ReturnSignedRequest request);
}
