package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DiscountResultVO {

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal payAmount;

    /** 实际生效的优惠金额中商家承担的部分 */
    private BigDecimal merchantBearAmount;

    /** 实际生效的优惠金额中平台承担（补贴）的部分 */
    private BigDecimal platformBearAmount;

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    /** 实际生效的全部促销活动 id（平台 + 商家活动可并存） */
    private List<Long> appliedPromotionIds;

    /** 本单可参与的促销活动选项（含未选中的，供结算页勾选展示） */
    private List<PromotionOption> promotionOptions;

    /** 每行商品的优惠拆解（原价小计 / 参与了哪些活动与券 / 优惠后小计） */
    private List<ItemDiscount> itemDiscounts;

    private Boolean promotionCouponStackable;

    /** 主优惠券（优惠额最大的一张），展示用；完整列表见 appliedCoupons */
    private Long appliedCouponId;

    private String appliedCouponTitle;

    /** 实际生效的优惠券明细（一单多券） */
    private List<CouponUsage> appliedCoupons;

    /** 一张生效优惠券在本单的抵扣明细 */
    @Data
    public static class CouponUsage {

        private Long couponId;

        private String title;

        private BigDecimal discount;
    }

    /** 结算页可勾选的促销活动选项：优惠只作用于各自范围内商品 */
    @Data
    public static class PromotionOption {

        private Long promotionId;

        private String title;

        /** 0 平台活动；>0 商家活动（归属店铺） */
        private Long shopId;

        private String shopName;

        /** 适用范围描述（全品类/指定分类/指定商品/本店商品） */
        private String scopeText;

        /** 规则描述，如「每满 200 减 30」「9 折，最多减 50」 */
        private String ruleText;

        /** 本单可减金额（只按范围内商品小计计算） */
        private BigDecimal discount;

        /** 该活动是否可与优惠券同用 */
        private Boolean couponStackable;

        /** 选中 = 当前算价实际生效的活动 */
        private Boolean chosen;
    }

    /** 单行商品的优惠拆解 */
    @Data
    public static class ItemDiscount {

        private Long productId;

        /** 原价小计（单价 × 数量） */
        private BigDecimal originalSubtotal;

        /** 本行分摊到的活动优惠 */
        private BigDecimal promotionDiscount;

        /** 本行分摊到的优惠券抵扣 */
        private BigDecimal couponDiscount;

        /** 优惠后小计 = 原价小计 − 活动优惠 − 券抵扣 */
        private BigDecimal finalSubtotal;

        /** 本行参与的活动（名称 + 本行分摊额） */
        private List<AppliedHit> promotions;

        /** 本行参与的优惠券（名称 + 本行抵扣额） */
        private List<AppliedHit> coupons;
    }

    /** 一项优惠在某行商品上的分摊明细 */
    @Data
    public static class AppliedHit {

        private Long id;

        private String title;

        private BigDecimal amount;
    }
}
