package com.four_person.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryRequest {

    /** 0 表示大类，否则为父分类 id */
    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private String icon;

    private String color;

    private Integer sort = 0;
}
