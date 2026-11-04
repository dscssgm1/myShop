package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderPreviewVO {

    private List<OrderPreviewItemVO> items;

    /** 按店铺拆分的预览 */
    private List<ShopPreviewVO> shops;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    /** 优惠中商家承担的部分 */
    private BigDecimal merchantBearAmount;

    /** 优惠中平台承担（补贴）的部分 */
    private BigDecimal platformBearAmount;

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    /** 主优惠券（优惠额最大的一张），展示用；完整列表见 appliedCoupons */
    private Long appliedCouponId;

    private String appliedCouponTitle;

    /** 实际生效的优惠券明细（一单多券） */
    private List<CouponUsageVO> appliedCoupons;

    /** 实际生效的促销活动 id 列表（用户未指定时为引擎自动选的最优组合） */
    private List<Long> appliedPromotionIds;

    /** 结算页可勾选的促销活动选项（本单可参与的全部活动，chosen 标记当前生效项） */
    private List<PromotionOptionVO> promotionOptions;

    /** 当前积分余额（前端展示可用积分） */
    private Integer pointsAvailable;

    /** 本次抵扣消耗的积分（usePoints=false 时为 0） */
    private Integer pointsUsed;

    /** 积分抵现金额（平台承担，已从 payAmount 中扣除） */
    private BigDecimal pointsDeduction;

    /** 抵现配置：多少积分抵 1 元；0 表示抵现未开启 */
    private Integer pointsPerYuan;

    @Data
    public static class CouponUsageVO {
        private Long couponId;
        private String title;
        private BigDecimal discount;
    }

    /** 结算页促销活动选项：本单可减金额 + 规则文案，chosen = 当前算价实际生效 */
    @Data
    public static class PromotionOptionVO {
        private Long promotionId;
        private String title;
        /** null/0 = 平台活动 */
        private Long shopId;
        private String shopName;
        private String scopeText;
        private String ruleText;
        /** 本单可减金额 */
        private BigDecimal discount;
        /** 是否可与优惠券同用 */
        private Boolean couponStackable;
        private Boolean chosen;
    }

    @Data
    public static class ShopPreviewVO {
        private Long shopId;
        private String shopName;
        private BigDecimal originalAmount;
        private BigDecimal promotionDiscount;
        private BigDecimal couponDiscount;
        private BigDecimal freightAmount;
        private BigDecimal payAmount;
        private List<OrderPreviewItemVO> items;
    }
}
