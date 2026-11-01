package com.four_person.logistics.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("logistics_company")
public class LogisticsCompany {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String code;
    private String contact;
    private String phone;
    private String color;

    /** 1 已入驻 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
