package com.four_person.order.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.order.api.dto.CreateOrderVO;
import com.four_person.order.api.dto.OrderCreateRequest;
import com.four_person.order.api.dto.OrderPreviewRequest;
import com.four_person.order.api.dto.OrderPreviewVO;
import com.four_person.order.api.dto.OrderVO;
import com.four_person.order.api.dto.ShipRequest;
import com.four_person.order.application.OrderApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    @PostMapping("/preview")
    public Result<OrderPreviewVO> preview(@Valid @RequestBody OrderPreviewRequest request) {
        return Result.success(orderApplicationService.preview(UserContext.getUserId(), request));
    }

    /** 提交订单：按店铺拆分，每店生成一个独立订单 */
    @PostMapping
    public Result<CreateOrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success("订单已创建", orderApplicationService.create(UserContext.getUserId(), request));
    }

    @GetMapping
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderApplicationService.page(UserContext.getUserId(), status, page, size));
    }

    @GetMapping("/admin")
    public Result<PageResult<OrderVO>> adminPage(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(orderApplicationService.adminPage(status, page, size));
    }

    /** 商家端：本店铺订单 */
    @GetMapping("/merchant")
    public Result<PageResult<OrderVO>> merchantPage(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        return Result.success(orderApplicationService.merchantPage(
                UserContext.requireShopId(), status, page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderApplicationService.detail(UserContext.getUserId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderApplicationService.cancel(UserContext.getUserId(), orderNo);
        return Result.success();
    }

    /** 买家确认收货（按订单） */
    @PostMapping("/{orderNo}/confirm")
    public Result<Void> confirm(@PathVariable String orderNo) {
        orderApplicationService.confirm(UserContext.getUserId(), orderNo);
        return Result.success();
    }

    /** 商家发货（只能发本店铺订单）：订单服务编排创建运单并标记已发货 */
    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo, @Valid @RequestBody ShipRequest request) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        orderApplicationService.ship(UserContext.getShopId(), orderNo, request.getCompanyId());
        return Result.success();
    }
}
