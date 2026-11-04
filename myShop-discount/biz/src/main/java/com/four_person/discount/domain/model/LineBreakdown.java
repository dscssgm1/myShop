package com.four_person.discount.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单行商品的优惠拆解（结算页逐行展示用）。
 *
 * @param promotions 本行参与的活动（名称 + 本行分摊额）
 * @param coupons    本行参与的优惠券（名称 + 本行抵扣额）
 */
public record LineBreakdown(Long productId,
                            BigDecimal originalSubtotal,
                            BigDecimal promotionDiscount,
                            BigDecimal couponDiscount,
                            BigDecimal finalSubtotal,
                            List<Hit> promotions,
                            List<Hit> coupons) {

    /** 一项优惠在某行商品上的分摊明细 */
    public record Hit(Long id, String title, BigDecimal amount) {
    }
}
