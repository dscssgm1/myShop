package com.four_person.platform.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("log_operation")
public class LogOperation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String userName;
    private String action;
    private String module;
    private String detail;
    private LocalDateTime createdAt;
}
