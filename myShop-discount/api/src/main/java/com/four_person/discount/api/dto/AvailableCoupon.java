package com.four_person.discount.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计算折扣时使用的用户优惠券视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableCoupon {

    private Long userCouponId;

    private Long templateId;

    private String title;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private String scopeType;

    private String scopeIds;

    private LocalDateTime expiredAt;
}
