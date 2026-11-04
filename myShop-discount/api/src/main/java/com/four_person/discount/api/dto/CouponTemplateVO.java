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

    /** 积分兑换价：NULL/0 = 普通免费领取券；>0 = 仅在积分商城用积分兑换 */
    private Integer pointsPrice;

    /** 是否可与促销活动同用 */
    private Boolean couponStackable;

    /** 是否可与其他优惠券叠加 */
    private Boolean stackable;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private String status;

    /** 当前用户是否已领取 */
    private Boolean claimed;

    /** 本单可抵扣金额（仅「结算页本单可领」接口返回；范围内小计达门槛后的实际抵扣额） */
    private BigDecimal applicableAmount;
}
