package com.four_person.discount.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("promotion")
public class PromotionPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String type;

    private String ruleType;

    private BigDecimal thresholdAmount;

    private Integer perEvery;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private BigDecimal maxDiscount;

    private String scopeType;

    private String scopeIds;

    private Boolean couponStackable;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private String description;

    private String color;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
