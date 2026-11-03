package com.four_person.system.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 认证相关契约：注册、登录、邮箱验证码、修改邮箱与密码。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Data
    public static class RegisterRequest {

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度需为 3-32 位")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字与下划线")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
        private String password;

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最长 50 位")
        private String nickname;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "邮箱验证码不能为空")
        private String emailCode;

        @Pattern(regexp = "^1\\d{10}$", message = "手机号需 11 位")
        private String phone;
    }

    @Data
    public static class LoginRequest {

        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginResponse {

        private String token;
        private String tokenType = "Bearer";
        private long expiresIn;
        private UserDtos.UserVO user;
        /** 注册等场景随登录返回的短时效身份验证凭证，用于「信任此设备」提示 */
        private String deviceVerifyToken;
    }

    @Data
    public static class EmailCodeRequest {

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        /** CHANGE_EMAIL / REGISTER，默认 CHANGE_EMAIL */
        private String purpose;
    }

    @Data
    public static class EmailCodeResponse {

        /** 有效期（秒） */
        private int expiresIn;

        /** 原型/调试模式下直接返回验证码；生产环境由邮件发送并返回 null */
        private String code;
    }

    @Data
    public static class ChangeEmailRequest {

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        /** 发往新邮箱的验证码（始终必填） */
        @NotBlank(message = "验证码不能为空")
        private String code;

        /** 未信任设备需出示身份验证凭证（向当前邮箱验证后获得） */
        private String verifyToken;
    }

    @Data
    public static class ChangePasswordRequest {

        @NotBlank(message = "当前密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位")
        private String newPassword;

        /** 未信任设备需出示身份验证凭证（向当前邮箱验证后获得） */
        private String verifyToken;
    }

    @Data
    public static class ForgotPasswordRequest {

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        @NotBlank(message = "验证码不能为空")
        private String code;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位")
        private String newPassword;
    }

    @Data
    public static class DeviceVerifyTokenVO {

        /** 短时效身份验证凭证（10 分钟），用于「信任此设备」提示 */
        private String verifyToken;
    }

    /** 在线登录设备（一个会话对应一次登录） */
    @Data
    public static class SessionVO {

        /** 会话 ID（JWT 的 jti），用于单点下线 */
        private String sessionId;

        private Long userId;

        private String username;

        private String deviceId;

        private String deviceName;

        private String ip;

        /** 登录时间，ISO-8601 字符串 */
        private String loginAt;

        /** 是否为当前请求所在的设备 */
        private boolean current;
    }
}
