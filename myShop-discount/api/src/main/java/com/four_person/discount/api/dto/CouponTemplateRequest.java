package com.four_person.discount.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CouponTemplateRequest {

    @NotBlank(message = "优惠券名称不能为空")
    private String title;

    @NotNull(message = "使用门槛不能为空")
    @DecimalMin(value = "0.00", message = "使用门槛不能为负数")
    private BigDecimal thresholdAmount;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额必须大于 0")
    private BigDecimal discountAmount;

    /** ALL（平台券：全品类）/ CATEGORY（指定分类）；商家券 SHOP（本店全部商品）/ CATEGORY */
    private String scopeType;

    /** CATEGORY 时为分类 ID 列表 */
    private List<Long> scopeIds;

    @NotNull(message = "发放总量不能为空")
    @Min(value = 1, message = "发放总量必须大于 0")
    private Integer totalStock;

    @NotNull(message = "每人限领数量不能为空")
    @Min(value = 1, message = "每人限领数量必须大于 0")
    private Integer perUserLimit;

    /** 积分兑换价：NULL/0 = 普通免费领取券；>0 = 仅在积分商城用积分兑换 */
    @Min(value = 0, message = "积分价不能为负数")
    private Integer pointsPrice;

    /** 是否可与促销活动同用（缺省视为可） */
    private Boolean couponStackable;

    /** 是否可与其他优惠券叠加（缺省视为可） */
    private Boolean stackable;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private String status;
}
