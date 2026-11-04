package com.four_person.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderPreviewRequest {

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<OrderItemRequest> items;

    /** 指定使用的优惠券（user_coupon.id），可多张叠加；为空时自动选最优组合 */
    private List<Long> couponIds;

    /** 指定参与的促销活动（promotion.id），可多选；null = 自动选最优；空列表 = 不参与活动 */
    private List<Long> promotionIds;

    /** 是否使用积分抵现；服务端按配置比例与上限自动算最优抵扣 */
    private Boolean usePoints;
}
