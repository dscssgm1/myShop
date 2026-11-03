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

    /** 积分商城兑换下单：免支付订单，积分全额抵扣 */
    @Data
    public static class RedeemOrderRequest {
        @NotNull(message = "用户不能为空")
        private Long userId;
        @NotNull(message = "商品不能为空")
        private Long productId;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        /** 消耗积分（仅入通知文案） */
        private Integer pointsUsed;
    }
}
