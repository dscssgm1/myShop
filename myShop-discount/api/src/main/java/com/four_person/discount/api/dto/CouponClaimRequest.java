package com.four_person.discount.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 积分商城兑换券：system 服务扣积分成功后，代用户领取优惠券 */
@Data
public class CouponClaimRequest {

    @NotNull(message = "用户不能为空")
    private Long userId;

    @NotNull(message = "券模板不能为空")
    private Long templateId;
}
