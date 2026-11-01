package com.four_person.product.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ProductBatchRequest {

    @NotEmpty(message = "商品 ID 列表不能为空")
    private List<Long> ids;
}
