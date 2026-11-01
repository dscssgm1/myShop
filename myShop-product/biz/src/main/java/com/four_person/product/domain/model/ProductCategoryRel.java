package com.four_person.product.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("product_category_rel")
public class ProductCategoryRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private Long categoryId;
}
