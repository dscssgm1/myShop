package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 商品佣金：流水 / 汇总 VO */
public class CommissionDtos {

    /** 单条佣金流水 */
    @Data
    public static class CommissionVO {
        private Long id;
        private String orderNo;
        /** COMMISSION 订单完成计佣 / REVERSAL 售后退款冲销 */
        private String type;
        private String typeText;
        private Long shopId;
        private String shopName;
        /** 计入佣金的商品实收金额（冲销为负） */
        private BigDecimal goodsAmount;
        /** 综合佣金费率%（记账时快照） */
        private BigDecimal commissionRate;
        private BigDecimal commissionAmount;
        private BigDecimal merchantAmount;
        private String detail;
        private String reason;
        private LocalDateTime createdAt;
    }

    /** 商家账单汇总：只统计本店铺 */
    @Data
    public static class CommissionSummaryVO {
        private BigDecimal totalGoodsAmount = BigDecimal.ZERO;
        private BigDecimal totalCommissionAmount = BigDecimal.ZERO;
        private BigDecimal totalMerchantAmount = BigDecimal.ZERO;
        private long commissionCount;
        private long reversalCount;
    }

    /** 平台账单汇总：全平台 + 各店铺账户 */
    @Data
    public static class CommissionAdminSummaryVO {
        private BigDecimal totalGoodsAmount = BigDecimal.ZERO;
        private BigDecimal totalCommissionAmount = BigDecimal.ZERO;
        private BigDecimal totalMerchantAmount = BigDecimal.ZERO;
        private long commissionCount;
        private long reversalCount;
        private List<ShopCommissionVO> shops;
    }

    /** 各店铺累计佣金 */
    @Data
    public static class ShopCommissionVO {
        private Long shopId;
        private String shopName;
        private BigDecimal goodsAmount = BigDecimal.ZERO;
        private BigDecimal commissionAmount = BigDecimal.ZERO;
        private BigDecimal merchantAmount = BigDecimal.ZERO;
        private long count;
    }
}
