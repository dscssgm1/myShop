package com.four_person.merchant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

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
        private Integer status;
    }

    @Data
    public static class ShopRequest {

        @NotBlank(message = "店铺名称不能为空")
        private String name;

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

        private String description;
    }

    @Data
    public static class ApplyVO {

        private Long id;
        private String shopName;
        private List<String> categories;
        private String contact;
        private String phone;
        private String description;
        private String status;
        private String reason;
        private LocalDateTime createdAt;
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
