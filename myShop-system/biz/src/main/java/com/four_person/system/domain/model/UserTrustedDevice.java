package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 信任设备：完成任一邮箱验证码验证后可标记，之后敏感操作免邮箱身份验证 */
@Data
@TableName("sys_trusted_device")
public class UserTrustedDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String deviceId;

    private String deviceName;

    private LocalDateTime trustedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
