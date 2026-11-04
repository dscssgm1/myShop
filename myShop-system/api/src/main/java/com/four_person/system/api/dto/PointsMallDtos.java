package com.four_person.system.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分商城（积分兑换优惠券）契约。
 */
public final class PointsMallDtos {

    private PointsMallDtos() {
    }

    /** 货架上的积分兑换券 */
    @Data
    public static class CouponVO {
        private Long templateId;
        private String title;
        /** 0 平台券；>0 商家券 */
        private Long shopId;
        private String shopName;
        /** 使用门槛（元），0 无门槛 */
        private BigDecimal thresholdAmount;
        /** 面额（元） */
        private BigDecimal discountAmount;
        private String scopeText;
        /** 兑换所需积分 */
        private Integer pointsPrice;
        private Integer totalStock;
        private Integer remaining;
        private Integer perUserLimit;
        /** 当前用户是否已达限领上限 */
        private Boolean claimed;
        private LocalDateTime validEnd;
    }

    @Data
    public static class ExchangeRequest {
        @NotNull(message = "请选择要兑换的优惠券")
        private Long templateId;
    }

    @Data
    public static class ExchangeVO {
        private Long id;
        private Long itemId;
        private String itemName;
        private String itemEmoji;
        private Integer points;
        private String orderNo;
        private LocalDateTime createdAt;
    }
}
