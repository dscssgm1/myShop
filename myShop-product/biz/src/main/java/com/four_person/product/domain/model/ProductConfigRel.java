package com.four_person.product.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品与服务保障配置 key 的关联（config_key 对应配置中心 service-guarantee 组的 key）。
 */
@Data
@TableName("product_config_rel")
public class ProductConfigRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private String configKey;
}
