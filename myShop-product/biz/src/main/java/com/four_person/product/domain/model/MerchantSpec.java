package com.four_person.product.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商家自建规格（我的规格库），发布商品时从这里选择规格与规格值 */
@Data
@TableName("merchant_spec")
public class MerchantSpec {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;
    private String name;
    /** 规格值 JSON 数组，如 ["红色","蓝色"] */
    private String options;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
