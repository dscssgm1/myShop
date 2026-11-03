package com.four_person.discount.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算时用户可用优惠券视图（按当前商品明细计算实际可抵扣金额）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableCoupon {

    private Long userCouponId;

    private Long templateId;

    private String title;

    private BigDecimal thresholdAmount;

    /** 券面金额 */
    private BigDecimal discountAmount;

    /** 在当前商品明细下实际可抵扣的金额（不满足门槛为 0） */
    private BigDecimal applicableAmount;

    private String scopeType;

    private String scopeText;

    /** 商家券发放店铺（平台券为 null） */
    private Long shopId;

    private String shopName;

    private LocalDateTime expiredAt;
}
