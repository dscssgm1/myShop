package com.four_person.logistics.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.logistics.application.ShipmentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供订单服务调用的内部接口：正向发货、售后退货运单与预约取件。
 */
@RestController
@RequestMapping("/internal/logistics")
@RequiredArgsConstructor
public class InternalLogisticsController {

    private final ShipmentApplicationService shipmentService;

    /** 订单服务编排商家发货：创建正向运单（同订单在途运单幂等返回） */
    @PostMapping("/shipments")
    public Result<LogisticsDtos.ShipmentVO> createShipment(
            @Valid @RequestBody LogisticsDtos.ShipmentCreateRequest request) {
        return Result.success(shipmentService.createInternal(request));
    }

    @PostMapping("/return-shipments")
    public Result<LogisticsDtos.ShipmentVO> createReturnShipment(
            @Valid @RequestBody LogisticsDtos.ReturnShipmentRequest request) {
        return Result.success(shipmentService.createReturn(request));
    }

    @GetMapping("/shipments/{trackingNo}")
    public Result<LogisticsDtos.ShipmentVO> shipment(@PathVariable String trackingNo) {
        return Result.success(shipmentService.byTrackingNo(trackingNo));
    }

    @PutMapping("/shipments/{trackingNo}/appointment")
    public Result<LogisticsDtos.ShipmentVO> appointment(
            @PathVariable String trackingNo,
            @Valid @RequestBody LogisticsDtos.ShipmentAppointmentRequest request) {
        return Result.success(shipmentService.appointment(trackingNo, request.getAppointmentTime()));
    }
}
