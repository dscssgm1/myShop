package com.four_person.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;
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

    /** 平台佣金费率（%）；小类为空时继承大类，大类为空表示不抽佣 */
    private BigDecimal commissionRate;

    /** 实际生效费率（%）：小类未单独设置时已回填大类费率 */
    private BigDecimal effectiveCommissionRate;

    /** 大类下的小类 */
    private List<CategoryVO> children;
}
