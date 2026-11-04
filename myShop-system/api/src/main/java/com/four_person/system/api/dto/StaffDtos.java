package com.four_person.system.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class StaffDtos {

    private StaffDtos() {
    }

    @Data
    public static class StaffVO {
        private Long id;
        private String username;
        private String nickname;
        private String phone;
        private String email;
        private String roleCode;
        private String roleName;
        private Integer status;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class CreateRequest {
        @NotBlank(message = "请填写登录名")
        @Size(min = 3, max = 32, message = "登录名 3-32 位")
        private String username;
        @NotBlank(message = "请填写初始密码")
        @Size(min = 6, max = 32, message = "密码 6-32 位")
        private String password;
        @NotBlank(message = "请填写姓名")
        private String nickname;
        @NotBlank(message = "请选择角色")
        private String roleCode;
        private String phone;
        private String email;
    }

    @Data
    public static class UpdateRequest {
        private String nickname;
        private String roleCode;
        private String phone;
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "请填写新密码")
        @Size(min = 6, max = 32, message = "密码 6-32 位")
        private String newPassword;
    }

    @Data
    public static class RoleMenusRequest {
        private List<String> keys;
    }
}
