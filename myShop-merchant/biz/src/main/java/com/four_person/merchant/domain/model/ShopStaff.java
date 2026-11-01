package com.four_person.merchant.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop_staff")
public class ShopStaff {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;
    private Long userId;
    private String name;
    private String phone;
    private String roleCode;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
