package com.four_person.product.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class CategoryVO {

    private Long id;

    private Long parentId;

    /** 1 大类 2 小类 */
    private Integer level;

    private String name;

    private String icon;

    private String color;

    private Integer sort;

    /** 大类下的小类 */
    private List<CategoryVO> children;
}
