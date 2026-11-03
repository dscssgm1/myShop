package com.four_person.product.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class StockChangeRequest {

    @NotEmpty(message = "库存变更明细不能为空")
    @Valid
    private List<StockItem> items;

    @Data
    public static class StockItem {

        @NotNull(message = "商品 ID 不能为空")
        private Long productId;

        /** 规格 SKU id，无规格商品为空 */
        private Long skuId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于 0")
        private Integer quantity;
    }
}
