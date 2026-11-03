package com.four_person.platform.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行政区划（省/市/县三级）。主键为 6 位行政区划代码，种子数据即全国区划，
 * 平台可在配置中心增删改（新增节点的代码按父级前缀自动生成）。
 */
@Data
@TableName("region")
public class Region {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 父级代码，省级为 0 */
    private Long parentId;

    private String name;

    /** 1 省 2 市 3 县区 */
    private Integer level;

    /** 1 启用 0 停用（停用后选择器不展示） */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
