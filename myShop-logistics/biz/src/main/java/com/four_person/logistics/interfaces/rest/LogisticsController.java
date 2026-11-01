package com.four_person.logistics.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.logistics.application.LogisticsCompanyService;
import com.four_person.logistics.application.ShipmentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsCompanyService companyService;
    private final ShipmentApplicationService shipmentService;

    // ---------------- 物流公司 ----------------

    /** 已入驻物流公司（商家发货选择） */
    @GetMapping("/companies")
    public Result<List<LogisticsDtos.CompanyVO>> companies(@RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            AccessGuard.requireGroup("PLATFORM");
        }
        return Result.success(companyService.list(all));
    }

    @PostMapping("/companies")
    public Result<LogisticsDtos.CompanyVO> createCompany(@Valid @RequestBody LogisticsDtos.CompanyRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("物流公司已新增", companyService.create(request));
    }

    @PutMapping("/companies/{id}/status")
    public Result<Void> updateCompanyStatus(@PathVariable Long id, @RequestParam Integer status) {
        AccessGuard.requireGroup("PLATFORM");
        companyService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    // ---------------- 快递员 ----------------

    @GetMapping("/couriers")
    public Result<List<LogisticsDtos.CourierVO>> couriers(@RequestParam(required = false) Long companyId) {
        AccessGuard.requireGroup("LOGISTICS", "PLATFORM");
        return Result.success(companyService.couriers(companyId));
    }

    @PostMapping("/couriers")
    public Result<LogisticsDtos.CourierVO> createCourier(@Valid @RequestBody LogisticsDtos.CourierRequest request) {
        AccessGuard.requireGroup("LOGISTICS");
        return Result.success("快递员已新增", companyService.createCourier(request));
    }

    @PutMapping("/couriers/{id}")
    public Result<LogisticsDtos.CourierVO> updateCourier(@PathVariable Long id,
                                                         @Valid @RequestBody LogisticsDtos.CourierRequest request) {
        AccessGuard.requireGroup("LOGISTICS");
        return Result.success("快递员已更新", companyService.updateCourier(id, request));
    }

    @DeleteMapping("/couriers/{id}")
    public Result<Void> deleteCourier(@PathVariable Long id) {
        AccessGuard.requireGroup("LOGISTICS");
        companyService.deleteCourier(id);
        return Result.success("快递员已删除", null);
    }

    /** 快递员任务：type=pickup 揽收，type=delivery 派送 */
    @GetMapping("/couriers/tasks")
    public Result<List<LogisticsDtos.ShipmentVO>> courierTasks(@RequestParam(defaultValue = "pickup") String type) {
        AccessGuard.requireGroup("LOGISTICS");
        Long courierId = UserContext.getCourierId();
        if (courierId == null) {
            return Result.success(List.of());
        }
        return Result.success(shipmentService.courierTasks(courierId, type));
    }

    // ---------------- 运单 ----------------

    /** 商家发货 */
    @PostMapping("/shipments")
    public Result<LogisticsDtos.ShipmentVO> createShipment(@Valid @RequestBody LogisticsDtos.ShipmentCreateRequest request) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success("运单已创建", shipmentService.create(request));
    }

    @GetMapping("/shipments")
    public Result<PageResult<LogisticsDtos.ShipmentVO>> shipments(@RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String region,
                                                                  @RequestParam(required = false) Long companyId,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requireGroup("LOGISTICS", "PLATFORM");
        Long scope = companyId != null ? companyId : UserContext.getCompanyId();
        return Result.success(shipmentService.page(scope, status, region, page, size));
    }

    @GetMapping("/shipments/{id}")
    public Result<LogisticsDtos.ShipmentVO> shipment(@PathVariable Long id) {
        return Result.success(shipmentService.detail(id));
    }

    /** 买家查询订单对应的运单 */
    @GetMapping("/shipments/order/{orderNo}")
    public Result<LogisticsDtos.ShipmentVO> shipmentByOrder(@PathVariable String orderNo) {
        return Result.success(shipmentService.byOrderNo(orderNo));
    }

    @PutMapping("/shipments/{id}/pickup")
    public Result<LogisticsDtos.ShipmentVO> pickup(@PathVariable Long id,
                                                   @RequestBody(required = false) LogisticsDtos.ShipmentActionRequest request) {
        AccessGuard.requireGroup("LOGISTICS");
        Long courierId = request != null && request.getCourierId() != null
                ? request.getCourierId() : UserContext.getCourierId();
        return Result.success("已揽收", shipmentService.pickup(id, courierId));
    }

    @PutMapping("/shipments/{id}/transit")
    public Result<LogisticsDtos.ShipmentVO> transit(@PathVariable Long id,
                                                    @RequestBody(required = false) LogisticsDtos.ShipmentActionRequest request) {
        AccessGuard.requireGroup("LOGISTICS");
        return Result.success("运输信息已更新",
                shipmentService.transit(id, request == null ? null : request.getContent()));
    }

    @PutMapping("/shipments/{id}/out-for-delivery")
    public Result<LogisticsDtos.ShipmentVO> outForDelivery(@PathVariable Long id,
                                                           @RequestBody(required = false) LogisticsDtos.ShipmentActionRequest request) {
        AccessGuard.requireGroup("LOGISTICS");
        Long courierId = request != null && request.getCourierId() != null
                ? request.getCourierId() : UserContext.getCourierId();
        return Result.success("已开始派送", shipmentService.outForDelivery(id, courierId));
    }

    @PutMapping("/shipments/{id}/deliver")
    public Result<LogisticsDtos.ShipmentVO> deliver(@PathVariable Long id,
                                                    @RequestBody(required = false) LogisticsDtos.ShipmentActionRequest request) {
        AccessGuard.requireGroup("LOGISTICS");
        return Result.success("已签收", shipmentService.deliver(id, request == null ? null : request.getSigner()));
    }
}
