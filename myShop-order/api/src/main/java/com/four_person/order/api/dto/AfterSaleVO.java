package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AfterSaleVO {

    private String afterSaleNo;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private String shopName;

    private String type;

    private String typeText;

    private String reason;

    private String description;

    private BigDecimal amount;

    private String status;

    private String statusText;

    private String originalOrderStatus;

    /** USER 用户发起 / CS 客服代发起 */
    private String initiatorRole;

    private String initiatorText;

    private String merchantReason;

    private String platformReason;

    private String returnTrackingNo;

    private String pickupCode;

    private String recoveryOrderNo;

    private LocalDateTime appointmentTime;

    private LocalDateTime deadlineAt;

    private LocalDateTime reviewedAt;

    private LocalDateTime refundedAt;

    private LocalDateTime createdAt;

    /** 退货物流状态（来自物流服务） */
    private String returnShipmentStatus;

    private String returnShipmentStatusText;

    private String returnShipmentCompany;

    private List<OrderItemVO> items;
}
