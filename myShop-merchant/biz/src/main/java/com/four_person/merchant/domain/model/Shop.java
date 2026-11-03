package com.four_person.merchant.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
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

    /** 经营许可证号 */
    private String licenseNo;
    /** 经营许可证照片 URL */
    private String licenseImage;
    /** 法定代表人 */
    private String legalPerson;
    /** 注册地址 */
    private String registeredAddress;
    /** 经营范围 */
    private String businessScope;
    /** 营业期限 */
    private String businessTerm;

    private Integer sales;

    /** 店铺评分：所有商品所有评价的均值 */
    private BigDecimal rating;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
