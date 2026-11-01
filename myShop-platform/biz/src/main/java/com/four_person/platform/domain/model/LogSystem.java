package com.four_person.platform.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("log_system")
public class LogSystem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String level;
    private String module;
    private String message;
    private LocalDateTime createdAt;
}
