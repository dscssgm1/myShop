package com.four_person.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建售后单。用户可为自己发起；平台/商家客服可指定 userId 代用户发起。
 */
@Data
public class AfterSaleCreateRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
    @NotBlank(message = "请选择售后类型")
    private String type;

    @NotBlank(message = "请填写售后原因")
    @Size(max = 200, message = "售后原因不能超过 200 字")
    private String reason;

    @Size(max = 500, message = "问题描述不能超过 500 字")
    private String description;

    /** 客服代用户发起时填写；用户本人发起时留空 */
    private Long userId;
}
