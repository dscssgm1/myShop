package com.four_person.logistics.api;

import com.four_person.common.api.Result;
import com.four_person.logistics.api.dto.LogisticsDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 物流服务内部契约，供订单服务编排发货、创建售后退货运单与预约取件。
 */
@FeignClient(name = "myshop-logistics", url = "${myshop.clients.logistics-url:}")
public interface LogisticsInternalApi {

    /** 商家发货：创建正向运单（同订单在途运单幂等返回） */
    @PostMapping("/internal/logistics/shipments")
    Result<LogisticsDtos.ShipmentVO> createShipment(@RequestBody LogisticsDtos.ShipmentCreateRequest request);

    @PostMapping("/internal/logistics/return-shipments")
    Result<LogisticsDtos.ShipmentVO> createReturnShipment(
            @RequestBody LogisticsDtos.ReturnShipmentRequest request);

    @GetMapping("/internal/logistics/shipments/{trackingNo}")
    Result<LogisticsDtos.ShipmentVO> shipmentByTrackingNo(@PathVariable("trackingNo") String trackingNo);

    @PutMapping("/internal/logistics/shipments/{trackingNo}/appointment")
    Result<LogisticsDtos.ShipmentVO> appoint(@PathVariable("trackingNo") String trackingNo,
                                             @RequestBody LogisticsDtos.ShipmentAppointmentRequest request);
}
