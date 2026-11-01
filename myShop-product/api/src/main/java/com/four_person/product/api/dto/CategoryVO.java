package com.four_person.product.api.dto;

import lombok.Data;

@Data
public class CategoryVO {

    private Long id;

    private String name;

    private String icon;

    private String color;

    private Integer sort;
}
