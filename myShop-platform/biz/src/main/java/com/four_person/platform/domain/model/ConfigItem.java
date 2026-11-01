package com.four_person.platform.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("config_item")
public class ConfigItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configGroup;
    private String configKey;
    private String configValue;
    private String description;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
