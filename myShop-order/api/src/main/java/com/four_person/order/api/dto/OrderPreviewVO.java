package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderPreviewVO {

    private List<OrderPreviewItemVO> items;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    private Long appliedPromotionId;

    private String appliedPromotionTitle;

    private Long appliedCouponId;

    private String appliedCouponTitle;
}
