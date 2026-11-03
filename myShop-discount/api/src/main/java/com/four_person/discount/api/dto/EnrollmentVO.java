package com.four_person.discount.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动商品报名记录视图（商家查看自己的报名、平台审核列表共用）。
 */
@Data
public class EnrollmentVO {

    private Long id;

    private Long promotionId;

    private String promotionTitle;

    private Long shopId;

    private String shopName;

    private Long productId;

    private String productName;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private String statusText;

    /** 驳回原因 */
    private String reason;

    private LocalDateTime appliedAt;

    private LocalDateTime reviewedAt;
}
