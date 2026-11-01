package com.four_person.logistics.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shipment")
public class Shipment {

    public static final String WAITING_PICKUP = "WAITING_PICKUP";
    public static final String PICKED = "PICKED";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String DELIVERING = "DELIVERING";
    public static final String SIGNED = "SIGNED";

    @TableId(type = IdType.AUTO)
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
    private Long courierId;
    private String courierName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
