package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单。状态流转见 {@link com.four_person.order.api.enums.AfterSaleStatus}。
 */
@Data
@TableName("after_sale")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String afterSaleNo;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private String shopName;

    /** REFUND_ONLY / RETURN_REFUND */
    private String type;

    private String reason;

    private String description;

    private BigDecimal amount;

    private String status;

    private String originalOrderStatus;

    /** USER / CS */
    private String initiatorRole;

    private Long initiatorId;

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

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
