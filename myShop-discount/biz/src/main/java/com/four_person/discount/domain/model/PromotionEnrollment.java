package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;

import java.time.LocalDateTime;

/**
 * 平台招商活动的商品报名记录（一个活动 × 一个商品）。
 */
public class PromotionEnrollment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final Long id;
    private final Long promotionId;
    private final Long shopId;
    private final String shopName;
    private final Long productId;
    private final String productName;
    private final String status;
    private final String reason;
    private final LocalDateTime appliedAt;
    private final LocalDateTime reviewedAt;

    public PromotionEnrollment(Long id, Long promotionId, Long shopId, String shopName,
                               Long productId, String productName, String status, String reason,
                               LocalDateTime appliedAt, LocalDateTime reviewedAt) {
        if (promotionId == null || shopId == null || productId == null) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "报名信息不完整");
        }
        this.id = id;
        this.promotionId = promotionId;
        this.shopId = shopId;
        this.shopName = shopName == null ? "" : shopName;
        this.productId = productId;
        this.productName = productName == null ? "" : productName;
        this.status = status == null || status.isBlank() ? STATUS_PENDING : status;
        this.reason = reason == null ? "" : reason;
        this.appliedAt = appliedAt;
        this.reviewedAt = reviewedAt;
    }

    public static PromotionEnrollment apply(Long promotionId, Long shopId, String shopName,
                                            Long productId, String productName) {
        return new PromotionEnrollment(null, promotionId, shopId, shopName,
                productId, productName, STATUS_PENDING, "", LocalDateTime.now(), null);
    }

    public PromotionEnrollment approved() {
        return withStatus(STATUS_APPROVED, "", LocalDateTime.now());
    }

    public PromotionEnrollment rejected(String reason) {
        return withStatus(STATUS_REJECTED, reason == null || reason.isBlank() ? "不符合活动要求" : reason,
                LocalDateTime.now());
    }

    /** 被拒绝后重新报名：清空驳回原因，回到待审核 */
    public PromotionEnrollment reapplied() {
        return new PromotionEnrollment(id, promotionId, shopId, shopName, productId, productName,
                STATUS_PENDING, "", LocalDateTime.now(), null);
    }

    private PromotionEnrollment withStatus(String newStatus, String newReason, LocalDateTime reviewedAt) {
        return new PromotionEnrollment(id, promotionId, shopId, shopName, productId, productName,
                newStatus, newReason, appliedAt, reviewedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getPromotionId() {
        return promotionId;
    }

    public Long getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }
}
