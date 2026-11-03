package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 登录记录：每次成功登录追加一条（只增不改的审计日志） */
@Data
@TableName("sys_login_record")
public class UserLoginRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String ip;

    /** 前端生成的持久设备 UUID */
    private String deviceId;

    /** 设备描述，如 Windows · Chrome */
    private String deviceName;

    private LocalDateTime loginAt;
}
