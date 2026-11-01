package com.four_person.product.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockUpdateRequest {

    /** 本次增加的库存数量 */
    @NotNull(message = "补货数量不能为空")
    @Min(value = 1, message = "补货数量必须大于 0")
    private Integer quantity;
}
