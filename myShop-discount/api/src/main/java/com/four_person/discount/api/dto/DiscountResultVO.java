package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DiscountResultVO {

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal payAmount;

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    private Boolean promotionCouponStackable;

    private Long appliedCouponId;

    private String appliedCouponTitle;
}
