package com.four_person.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<OrderItemRequest> items;

    /** 指定使用的优惠券（user_coupon.id），可多张叠加；为空时自动选最优组合 */
    private List<Long> couponIds;

    /** 指定参与的促销活动（promotion.id），可多选；null = 自动选最优；空列表 = 不参与活动 */
    private List<Long> promotionIds;

    /** 是否使用积分抵现；服务端按配置比例与上限自动算最优抵扣，下单即扣积分 */
    private Boolean usePoints;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    private String remark;
}
