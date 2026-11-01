package com.four_person.product.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spec_option")
public class SpecOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;
    private String value;
    private Integer sort;
    private LocalDateTime createdAt;
}
