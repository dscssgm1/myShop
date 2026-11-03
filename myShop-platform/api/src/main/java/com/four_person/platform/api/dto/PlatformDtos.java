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
        private Long stage1By;
        private LocalDateTime stage1At;
        private LocalDateTime auditedAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class LogisticsApplyRequest {
        @NotBlank(message = "公司名称不能为空")
        private String name;
        @NotBlank(message = "公司编码不能为空")
        private String code;
        private String contact;
        private String phone;
        private String description;
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

    @Data
    public static class ShopVO {
        private Long id;
        private String name;
        private String icon;
        private String description;
        private String contact;
        private String phone;
        private String region;
        private String address;
        private String categories;
        private Integer sales;
        private java.math.BigDecimal rating;
        private Integer status;
        /** 最近一次启用/禁用原因 */
        private String statusReason;
        private LocalDateTime createdAt;

        /** 经营许可证号 */
        private String licenseNo;
        /** 经营许可证照片 URL */
        private String licenseImage;
        /** 法定代表人 */
        private String legalPerson;
        /** 注册地址 */
        private String registeredAddress;
        /** 经营范围 */
        private String businessScope;
        /** 营业期限 */
        private String businessTerm;
    }

    /** 平台：商家入驻申请 */
    @Data
    public static class ShopApplyVO {
        private Long id;
        private Long userId;
        private String username;
        private String nickname;
        private String email;
        private String shopName;
        private List<String> categories;
        private String contact;
        private String phone;
        private String region;
        private String address;
        private String description;
        /** PENDING / APPROVED / REJECTED */
        private String status;
        private String reason;
        private Long shopId;
        private LocalDateTime auditedAt;
        private LocalDateTime createdAt;

        private String licenseNo;
        private String licenseImage;
        private String legalPerson;
        private String registeredAddress;
        private String businessScope;
        private String businessTerm;
    }

    /** 平台：审核意见（驳回时必填） */
    @Data
    public static class ReviewRequest {
        private String reason;
    }
}
