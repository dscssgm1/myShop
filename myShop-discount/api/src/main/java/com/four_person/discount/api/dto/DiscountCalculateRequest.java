package com.four_person.discount.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DiscountCalculateRequest {

    /** 内部调用时由订单服务传入；为空时取请求头 X-User-Id */
    private Long userId;

    /** 用户指定使用的优惠券（user_coupon.id）；为空时引擎自动选最优组合 */
    private List<Long> couponIds;

    /**
     * 用户指定的促销活动（promotion.id）；null = 引擎自动选「最优平台活动 + 每店最优商家活动」，
     * 非 null（含空列表 = 不参与任何活动）则精确按所选活动生效，所选活动不适用时报错。
     */
    private List<Long> promotionIds;

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<DiscountItem> items;
}
