package com.four_person.platform.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("log_login")
public class LogLogin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String method;
    private String ip;
    private String result;
    private LocalDateTime createdAt;
}
