package com.four_person.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

public final class SpecDtos {

    private SpecDtos() {
    }

    @Data
    public static class SpecTemplateVO {
        private Long id;
        private String name;
        private Integer sort;
        private List<String> options;
    }

    @Data
    public static class SpecTemplateRequest {
        @NotBlank(message = "规格名称不能为空")
        private String name;

        @NotEmpty(message = "请至少提供一个规格选项")
        private List<String> options;

        private Integer sort = 0;
    }
}
