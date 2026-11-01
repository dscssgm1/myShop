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
    private String description;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
