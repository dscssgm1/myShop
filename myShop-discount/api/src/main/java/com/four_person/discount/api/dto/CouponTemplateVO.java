package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CouponTemplateVO {

    private Long id;

    /** 0 平台券；>0 商家券 */
    private Long shopId;

    /** 商家券时发放店铺名称 */
    private String shopName;

    private String title;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private String scopeType;

    private String scopeText;

    private List<Long> scopeIds;

    private Integer totalStock;

    private Integer claimedCount;

    private Integer remaining;

    private Integer perUserLimit;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private String status;

    /** 当前用户是否已领取 */
    private Boolean claimed;
}
