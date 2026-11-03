package com.four_person.logistics.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流分红流水。账务记录，不设逻辑删除。
 */
@Data
@TableName("dividend_record")
public class DividendRecord {

    /** 签收分红 */
    public static final String TYPE_DIVIDEND = "DIVIDEND";
    /** 平台调账（可为负） */
    public static final String TYPE_ADJUST = "ADJUST";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 运单号（调账记录为空）；唯一键保证同运单只结算一次 */
    private String trackingNo;

    private String type;

    private String orderNo;
    private Long shopId;
    private String shopName;
    private Long companyId;
    private String companyName;

    /** 订单运费 */
    private BigDecimal freightAmount;

    /** 平台分成比例%（记账时快照） */
    private BigDecimal platformRatio;

    private BigDecimal platformAmount;

    /** 物流公司分成金额（调账可为负） */
    private BigDecimal companyAmount;

    /** 调账原因 */
    private String reason;

    private LocalDateTime createdAt;
}
