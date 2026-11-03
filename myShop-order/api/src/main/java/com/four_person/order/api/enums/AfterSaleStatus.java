package com.four_person.order.api.enums;

import java.util.Set;

/**
 * 售后单状态。
 */
public enum AfterSaleStatus {

    PENDING_MERCHANT("待商家审核"),
    MERCHANT_REJECTED("商家已拒绝"),
    PENDING_PLATFORM("待平台审核"),
    APPROVED("已同意，待退款"),
    REFUNDED("已退款"),
    PLATFORM_REJECTED("平台已拒绝"),
    APPEALING("商家申诉中"),
    APPEAL_APPROVED("申诉成立，待补款");

    private final String text;

    AfterSaleStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static String textOf(String status) {
        try {
            return valueOf(status).getText();
        } catch (Exception ex) {
            return status;
        }
    }

    /** 售后流程是否已经走完，允许订单再次发起售后 */
    public static boolean isFinal(String status) {
        return PLATFORM_REJECTED.name().equals(status);
    }

    /** 仍在处理中（订单应保持售后中） */
    public static final Set<String> ACTIVE = Set.of(
            PENDING_MERCHANT.name(), MERCHANT_REJECTED.name(), PENDING_PLATFORM.name(),
            APPROVED.name(), APPEALING.name());
}
