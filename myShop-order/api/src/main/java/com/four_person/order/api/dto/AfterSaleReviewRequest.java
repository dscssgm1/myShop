package com.four_person.order.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家/平台审核售后。
 */
@Data
public class AfterSaleReviewRequest {

    @NotNull(message = "请选择审核结果")
    private Boolean approved;

    @Size(max = 255, message = "审核意见不能超过 255 字")
    private String reason;
}
