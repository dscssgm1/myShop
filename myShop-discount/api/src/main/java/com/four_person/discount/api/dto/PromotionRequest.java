package com.four_person.discount.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PromotionRequest {

    @NotBlank(message = "活动名称不能为空")
    private String title;

    /** 展示类型：平台大促 / 品类促销 / 限时秒杀 / 领券中心 */
    private String type;

    /** FULL_REDUCTION / DISCOUNT */
    @NotBlank(message = "优惠规则类型不能为空")
    private String ruleType;

    private BigDecimal thresholdAmount;

    private Integer perEvery;

    private BigDecimal discountAmount;

    private BigDecimal discountRate;

    private BigDecimal maxDiscount;

    /** ALL（平台活动：全品类）/ CATEGORY（指定分类）；商家活动 SHOP（本店全部商品）/ CATEGORY */
    private String scopeType;

    /** CATEGORY 时为分类 ID 列表 */
    private List<Long> scopeIds;

    private Boolean couponStackable;

    /** 是否需要商家报名商品参加（仅平台活动，商家活动固定 false） */
    private Boolean enrollRequired;

    /** 平台承担优惠比例（%），0~100；空/0 表示商家全额承担，商家活动固定 0 */
    private BigDecimal platformBearRate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private String description;

    private String color;
}
