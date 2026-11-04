package com.four_person.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

public final class OrderInternalDtos {

    private OrderInternalDtos() {
    }

    /** 物流服务回调：正向运单签收，订单记录签收时间并启动自动确认倒计时 */
    @Data
    public static class ShipmentSignedRequest {
        @NotBlank(message = "订单号不能为空")
        private String orderNo;
        @NotBlank(message = "运单号不能为空")
        private String trackingNo;
        /** 签收时间，空则取服务端当前时间 */
        private LocalDateTime signedAt;
    }

    /** 物流服务回调：售后退货运单已签收（商家收到退货） */
    @Data
    public static class ReturnSignedRequest {
        @NotBlank(message = "运单号不能为空")
        private String trackingNo;
    }
}
