package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品佣金流水。账务记录，不设逻辑删除。
 */
@Data
@TableName("commission_record")
public class CommissionRecord {

    /** 订单完成计佣 */
    public static final String TYPE_COMMISSION = "COMMISSION";
    /** 售后退款冲销（金额为负） */
    public static final String TYPE_REVERSAL = "REVERSAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（一店一单） */
    private String orderNo;

    private String type;

    private Long shopId;
    private String shopName;

    /** 计入佣金的商品实收金额（冲销为负） */
    private BigDecimal goodsAmount;

    /** 综合佣金费率%（记账时快照） */
    private BigDecimal commissionRate;

    /** 平台佣金（冲销为负） */
    private BigDecimal commissionAmount;

    /** 商家净得（冲销为负） */
    private BigDecimal merchantAmount;

    /** 各类目计佣明细 */
    private String detail;

    private String reason;

    private LocalDateTime createdAt;
}
