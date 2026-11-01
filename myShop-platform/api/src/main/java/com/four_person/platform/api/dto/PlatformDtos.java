package com.four_person.platform.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class PlatformDtos {

    private PlatformDtos() {
    }

    @Data
    public static class AuditCreateRequest {
        @NotBlank(message = "审核类型不能为空")
        private String type;
        private Long bizId;
        @NotBlank(message = "标题不能为空")
        private String title;
        private String detail;
        private String payload;
    }

    @Data
    public static class AuditActionRequest {
        private String reason;
    }

    @Data
    public static class AuditVO {
        private Long id;
        private String type;
        private Long bizId;
        private String title;
        private String detail;
        private Long applicantId;
        private String applicantName;
        private String status;
        private String reason;
        private String payload;
        private LocalDateTime auditedAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ConfigItemVO {
        private Long id;
        private String key;
        private String value;
        private String description;
    }

    @Data
    public static class ConfigSaveRequest {
        private List<ConfigItemVO> items;
    }

    @Data
    public static class OperationLogRequest {
        private Long userId;
        private String userName;
        private String action;
        private String module;
        private String detail;
    }

    @Data
    public static class LoginLogRequest {
        private Long userId;
        private String username;
        private String method;
        private String ip;
        private String result;
    }

    @Data
    public static class SystemLogRequest {
        private String level;
        private String module;
        private String message;
    }

    @Data
    public static class OperationLogVO {
        private Long id;
        private String userName;
        private String action;
        private String module;
        private String detail;
        private LocalDateTime createdAt;
    }

    @Data
    public static class LoginLogVO {
        private Long id;
        private String username;
        private String method;
        private String ip;
        private String result;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SystemLogVO {
        private Long id;
        private String level;
        private String module;
        private String message;
        private LocalDateTime createdAt;
    }
}
