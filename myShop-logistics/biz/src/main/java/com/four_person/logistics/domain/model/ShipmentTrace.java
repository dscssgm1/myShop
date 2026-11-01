package com.four_person.logistics.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shipment_trace")
public class ShipmentTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shipmentId;
    private String content;
    private LocalDateTime createdAt;
}
