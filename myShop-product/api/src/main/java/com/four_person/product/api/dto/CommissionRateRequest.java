package com.four_person.product.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量查询类目佣金费率 */
@Data
public class CommissionRateRequest {

    @NotNull(message = "类目 id 列表不能为空")
    private List<Long> categoryIds;
}
