package com.four_person.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public final class BrandDtos {

    private BrandDtos() {
    }

    @Data
    public static class BrandVO {
        private Long id;
        private String name;
        private String logo;
        private Integer sort;
    }

    @Data
    public static class BrandRequest {
        @NotBlank(message = "品牌名称不能为空")
        private String name;
        private String logo;
        private Integer sort = 0;
    }
}
