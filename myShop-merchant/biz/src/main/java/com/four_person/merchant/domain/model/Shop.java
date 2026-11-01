package com.four_person.merchant.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String icon;
    private String color;
    private String description;
    private String contact;
    private String phone;
    private String region;
    private String address;

    /** 经营类目 JSON 数组 */
    private String categories;

    private Integer sales;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
