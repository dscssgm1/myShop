package com.four_person.system.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户相关契约。
 */
public final class UserDtos {

    private UserDtos() {
    }

    @Data
    public static class UserVO {

        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String phone;
        private String avatar;
        private String roleCode;
        private String roleName;
        private String groupCode;
        private Long shopId;
        private Long companyId;
        private Long courierId;
        private Integer status;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class UpdateProfileRequest {

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最长 50 位")
        private String nickname;

        @Pattern(regexp = "^1\\d{10}$", message = "手机号需 11 位")
        private String phone;

        private String avatar;
    }

    @Data
    public static class UserQuery {

        private String keyword;
        private String roleCode;
        private Integer status;
        private Integer page = 1;
        private Integer size = 10;
    }

    @Data
    public static class UpdateStatusRequest {

        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能为 0 或 1")
        @Max(value = 1, message = "状态只能为 0 或 1")
        private Integer status;
    }

    @Data
    public static class UpdateRoleRequest {

        @NotBlank(message = "角色不能为空")
        private String roleCode;
    }
}
