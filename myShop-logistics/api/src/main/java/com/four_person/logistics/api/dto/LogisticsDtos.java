package com.four_person.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class LogisticsDtos {

    private LogisticsDtos() {
    }

    @Data
    public static class CompanyVO {
        private Long id;
        private String name;
        private String code;
        private String contact;
        private String phone;
        private String color;
        private Integer status;
    }

    @Data
    public static class CompanyRequest {
        @NotBlank(message = "物流公司名称不能为空")
        private String name;
        @NotBlank(message = "公司编码不能为空")
        private String code;
        private String contact;
        private String phone;
        private String color;
        private Integer status = 0;
    }

    @Data
    public static class CourierVO {
        private Long id;
        private Long companyId;
        private String name;
        private String phone;
        private String region;
        private Integer status;
    }

    @Data
    public static class CourierRequest {
        @NotBlank(message = "快递员姓名不能为空")
        private String name;
        private String phone;
        @NotBlank(message = "负责区域不能为空")
        private String region;
        private Integer status = 1;
    }

    @Data
    public static class ShipmentCreateRequest {
        @NotBlank(message = "订单号不能为空")
        private String orderNo;
        @NotNull(message = "物流公司不能为空")
        private Long companyId;
        private Long shopId;
        private String shopName;
        private String pickupAddress;
        private String pickupRegion;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private String itemsText;
    }

    @Data
    public static class ShipmentActionRequest {
        private Long courierId;
        private String signer;
        private String content;
    }

    @Data
    public static class TraceVO {
        private String content;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ShipmentVO {
        private Long id;
        private String trackingNo;
        private String orderNo;
        private Long shopId;
        private String shopName;
        private String pickupAddress;
        private String pickupRegion;
        private Long companyId;
        private String companyName;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private String receiverRegion;
        private String itemsText;
        private String status;
        private String statusText;
        private Long courierId;
        private String courierName;
        private LocalDateTime createdAt;
        private List<TraceVO> traces;
    }
}
