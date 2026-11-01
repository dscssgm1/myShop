package com.four_person.system.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色相关契约。
 */
public final class RoleDtos {

    private RoleDtos() {
    }

    @Data
    public static class RoleVO {

        private Long id;
        private String code;
        private String name;
        private String groupCode;
        private String icon;
        private String description;
        private Integer sort;
        private Integer status;
        private Long userCount;
    }

    @Data
    public static class RoleRequest {

        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9-]{2,31}$", message = "角色编码需以小写字母开头，仅含小写字母、数字与短横线")
        private String code;

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称最长 50 位")
        private String name;

        @NotBlank(message = "所属端不能为空")
        private String groupCode;

        private String icon;

        @Size(max = 200, message = "描述最长 200 位")
        private String description;

        private Integer sort = 0;

        private Integer status = 1;
    }
}
