package com.four_person.merchant.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop_apply")
public class ShopApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String shopName;
    private String categories;
    private String contact;
    private String phone;
    private String region;
    private String address;
    private String description;

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

    /** PENDING / APPROVED / REJECTED */
    private String status;

    /** 审核通过后生成的店铺 ID */
    private Long shopId;

    private String reason;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
