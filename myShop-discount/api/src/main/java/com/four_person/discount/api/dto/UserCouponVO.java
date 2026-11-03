package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserCouponVO {

    private Long id;

    private Long templateId;

    private String title;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private String scopeType;

    private String scopeText;

    /** 0 平台券；>0 商家券发放店铺 */
    private Long shopId;

    /** 商家券发放店铺名称 */
    private String shopName;

    private String status;

    private String statusText;

    private String orderNo;

    private LocalDateTime claimedAt;

    private LocalDateTime usedAt;

    private LocalDateTime expiredAt;
}
