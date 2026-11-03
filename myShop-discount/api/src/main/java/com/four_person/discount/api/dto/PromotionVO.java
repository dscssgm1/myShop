package com.four_person.discount.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PromotionVO {

    private Long id;

    /** 0 平台活动；>0 商家自建活动 */
    private Long shopId;

    /** 商家活动时发布店铺名称 */
    private String shopName;

    private String title;

    private String type;

    private String ruleType;

    private BigDecimal thresholdAmount;

    private Integer perEvery;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private BigDecimal maxDiscount;

    private String scopeType;

    private List<Long> scopeIds;

    private Boolean couponStackable;

    /** 是否需要商家报名商品参加 */
    private Boolean enrollRequired;

    /** 平台承担优惠比例（%） */
    private BigDecimal platformBearRate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private String statusText;

    private String description;

    private String color;
}
