package com.four_person.system.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人资产契约：收货地址、银行卡。
 */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    @Data
    public static class AddressRequest {

        @NotBlank(message = "收货人不能为空")
        @Size(max = 50, message = "收货人最长 50 位")
        private String receiverName;

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号需 11 位")
        private String receiverPhone;

        @NotBlank(message = "收货地址不能为空")
        @Size(max = 255, message = "收货地址最长 255 位")
        private String address;

        private Boolean isDefault = false;
    }

    @Data
    public static class AddressVO {

        private Long id;
        private String receiverName;
        private String receiverPhone;
        private String address;
        private Boolean isDefault;
        private LocalDateTime createdAt;
    }

    @Data
    public static class BankCardRequest {

        @NotBlank(message = "开户银行不能为空")
        private String bankName;

        @NotBlank(message = "卡号不能为空")
        @Pattern(regexp = "^\\d{16,19}$", message = "卡号需 16-19 位数字")
        private String cardNo;

        private String cardType = "储蓄卡";

        @NotBlank(message = "持卡人不能为空")
        private String holder;
    }

    @Data
    public static class BankCardVO {

        private Long id;
        private String bankName;
        /** 脱敏卡号：6225 **** **** 1234 */
        private String cardNo;
        private String cardType;
        private String holder;
        private LocalDateTime createdAt;
    }
}
