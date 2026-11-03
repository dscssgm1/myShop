package com.four_person.merchant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MerchantDtos {

    private MerchantDtos() {
    }

    @Data
    public static class ShopVO {

        private Long id;
        private String name;
        private String icon;
        private String color;
        private String description;
        private String contact;
        private String phone;
        private String region;
        private String address;
        private List<String> categories;
        private Integer sales;
        /** 店铺评分：所有商品所有评价的均值，无评价时为 null */
        private BigDecimal rating;
        private Integer status;

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
        /** 营业期限，如「2024-03-15 至 2034-03-14」 */
        private String businessTerm;
    }

    @Data
    public static class ShopRequest {

        @NotBlank(message = "店铺名称不能为空")
        private String name;

        @NotBlank(message = "请上传店铺标识图片")
        private String icon;
        private String color;
        private String description;
        private String contact;

        @Pattern(regexp = "^1\\d{10}$", message = "手机号需 11 位")
        private String phone;

        private String region;

        @NotBlank(message = "店铺地址（揽收地址）不能为空")
        private String address;

        @NotEmpty(message = "请至少选择一个经营类目")
        private List<String> categories;

        /** 资质信息（对买家公示，前后端均必填） */
        @NotBlank(message = "经营许可证号不能为空")
        private String licenseNo;

        @NotBlank(message = "请上传经营许可证照片")
        private String licenseImage;

        @NotBlank(message = "法定代表人不能为空")
        private String legalPerson;

        @NotBlank(message = "注册地址不能为空")
        private String registeredAddress;

        @NotBlank(message = "经营范围不能为空")
        private String businessScope;

        @NotBlank(message = "请选择营业期限")
        private String businessTerm;
    }

    @Data
    public static class ApplyRequest {

        @NotBlank(message = "店铺名称不能为空")
        private String shopName;

        @NotEmpty(message = "请至少选择一个经营类目")
        private List<String> categories;

        @NotBlank(message = "联系人不能为空")
        private String contact;

        @Pattern(regexp = "^1\\d{10}$", message = "手机号需 11 位")
        private String phone;

        @NotBlank(message = "请选择所在区域")
        private String region;

        @NotBlank(message = "请填写店铺地址")
        private String address;

        private String description;

        // ===== 工商资质信息（入驻必填） =====

        @NotBlank(message = "经营许可证号不能为空")
        private String licenseNo;

        @NotBlank(message = "请上传经营许可证照片")
        private String licenseImage;

        @NotBlank(message = "法定代表人不能为空")
        private String legalPerson;

        @NotBlank(message = "注册地址不能为空")
        private String registeredAddress;

        @NotBlank(message = "经营范围不能为空")
        private String businessScope;

        @NotBlank(message = "请选择营业期限")
        private String businessTerm;
    }

    @Data
    public static class ApplyVO {

        private Long id;
        private String shopName;
        private List<String> categories;
        private String contact;
        private String phone;
        private String region;
        private String address;
        private String description;
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

    @Data
    public static class StaffVO {

        private Long id;
        private String name;
        private String phone;
        private String roleCode;
        private Integer status;
    }

    @Data
    public static class StaffRequest {

        @NotBlank(message = "员工姓名不能为空")
        private String name;

        @Pattern(regexp = "^1\\d{10}$", message = "手机号需 11 位")
        private String phone;

        @NotBlank(message = "角色不能为空")
        private String roleCode;

        private Integer status = 1;
    }
}
