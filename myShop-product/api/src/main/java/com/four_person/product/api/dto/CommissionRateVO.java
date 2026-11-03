package com.four_person.product.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 类目生效佣金费率（小类未单独设置时已回填大类费率） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommissionRateVO {
    private Long categoryId;

    /** 类目名称，写佣金明细用 */
    private String categoryName;

    /** 费率（%），0 表示不抽佣 */
    private BigDecimal rate;
}
