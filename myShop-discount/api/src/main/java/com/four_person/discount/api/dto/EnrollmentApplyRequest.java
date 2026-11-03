package com.four_person.discount.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EnrollmentApplyRequest {

    @NotNull(message = "活动不能为空")
    private Long promotionId;

    @NotEmpty(message = "请选择要报名的商品")
    private List<Long> productIds;
}
