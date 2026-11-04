package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品展示「单件最低到手价」：按只买 1 件该商品计算，
 * 套用命中的促销活动与（登录用户）满足门槛的优惠券，与结算页共用同一套计价引擎。
 */
@Data
public class ProductPriceVO {

    private Long productId;

    /** 单件原价 */
    private BigDecimal originalPrice;

    /** 活动优惠金额 */
    private BigDecimal promotionDiscount;

    /** 优惠券抵扣金额（匿名用户恒为 0） */
    private BigDecimal couponDiscount;

    /** 单件到手价 = 原价 - 活动 - 券 */
    private BigDecimal finalPrice;

    /** 命中的活动明细 */
    private List<Hit> promotions;

    /** 命中的优惠券明细 */
    private List<Hit> coupons;

    @Data
    public static class Hit {

        private Long id;

        private String title;

        private BigDecimal amount;
    }
}
