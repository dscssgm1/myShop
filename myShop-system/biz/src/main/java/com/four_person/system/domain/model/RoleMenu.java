package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色勾选的菜单/按钮权限码（树定义在前端 perm/tree.js，前后端共用同一套 key） */
@Data
@TableName("sys_role_menu")
public class RoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleCode;

    private String permKey;

    private LocalDateTime createdAt;
}
