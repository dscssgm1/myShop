package com.four_person.logistics.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
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
        /** 售后退货运单揽收时需校验取件码 */
        private String pickupCode;
    }

    /** 订单服务在商家同意退货后创建退货运单 */
    @Data
    public static class ReturnShipmentRequest {
        @NotBlank(message = "售后单号不能为空")
        private String afterSaleNo;
        @NotBlank(message = "订单号不能为空")
        private String orderNo;
        private Long shopId;
        private String shopName;
        /** 上门取件地址（买家地址） */
        private String pickupAddress;
        private String pickupRegion;
        /** 退货收件方（商家地址） */
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private String receiverRegion;
        private String itemsText;
        @NotBlank(message = "取件码不能为空")
        private String pickupCode;
    }

    /** 用户预约上门取件时间（两天以内） */
    @Data
    public static class ShipmentAppointmentRequest {
        @NotNull(message = "请选择上门取件时间")
        private LocalDateTime appointmentTime;
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
        /** NORMAL 正向发货 / RETURN 售后退货 */
        private String type;
        private String typeText;
        private String afterSaleNo;
        private String pickupCode;
        private LocalDateTime appointmentTime;
        private Long courierId;
        private String courierName;
        private LocalDateTime createdAt;
        private List<TraceVO> traces;
    }

    // ---------------- 分红 ----------------

    /** 分红流水 */
    @Data
    public static class DividendVO {
        private Long id;
        private String trackingNo;
        /** DIVIDEND 签收分红 / ADJUST 平台调账 */
        private String type;
        private String typeText;
        private String orderNo;
        private String shopName;
        private String companyName;
        /** 订单运费 */
        private BigDecimal freightAmount;
        /** 平台分成比例%（记账时快照） */
        private BigDecimal platformRatio;
        private BigDecimal platformAmount;
        private BigDecimal companyAmount;
        private String reason;
        private LocalDateTime createdAt;
    }

    /** 物流公司视角分红汇总 */
    @Data
    public static class DividendSummaryVO {
        /** 分红账户余额 */
        private BigDecimal balance;
        private BigDecimal totalCompanyAmount;
        private BigDecimal totalPlatformAmount;
        private long dividendCount;
    }

    /** 平台视角分红汇总（含各公司账户） */
    @Data
    public static class DividendAdminSummaryVO {
        private BigDecimal totalPlatformAmount;
        private BigDecimal totalCompanyAmount;
        private long dividendCount;
        private long adjustCount;
        private List<CompanyDividendVO> companies;
    }

    @Data
    public static class CompanyDividendVO {
        private Long companyId;
        private String companyName;
        /** 分红账户余额 */
        private BigDecimal balance;
        private BigDecimal companyAmount;
        private BigDecimal platformAmount;
        private long dividendCount;
    }

    /** 平台调账：金额正为入账、负为扣减 */
    @Data
    public static class DividendAdjustRequest {
        @NotNull(message = "物流公司不能为空")
        private Long companyId;
        @NotNull(message = "调整金额不能为空")
        private BigDecimal amount;
        @NotBlank(message = "请填写调账原因")
        private String reason;
    }
}
