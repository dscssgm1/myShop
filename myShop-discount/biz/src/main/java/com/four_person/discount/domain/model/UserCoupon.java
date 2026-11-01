package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.CouponStatus;
import com.four_person.discount.enums.DiscountErrorCode;

import java.time.LocalDateTime;

/**
 * 用户优惠券聚合根，负责使用/释放/过期状态流转。
 */
public class UserCoupon {

    private final Long id;
    private final Long userId;
    private final Long templateId;
    private final CouponStatus status;
    private final String orderNo;
    private final LocalDateTime claimedAt;
    private final LocalDateTime usedAt;
    private final LocalDateTime expiredAt;

    public UserCoupon(Long id, Long userId, Long templateId, CouponStatus status, String orderNo,
                      LocalDateTime claimedAt, LocalDateTime usedAt, LocalDateTime expiredAt) {
        this.id = id;
        this.userId = userId;
        this.templateId = templateId;
        this.status = status == null ? CouponStatus.UNUSED : status;
        this.orderNo = orderNo;
        this.claimedAt = claimedAt;
        this.usedAt = usedAt;
        this.expiredAt = expiredAt;
    }

    public static UserCoupon claim(Long userId, Long templateId, LocalDateTime claimedAt, LocalDateTime expiredAt) {
        return new UserCoupon(null, userId, templateId, CouponStatus.UNUSED, null, claimedAt, null, expiredAt);
    }

    public boolean isUsable(LocalDateTime now) {
        return status == CouponStatus.UNUSED && !isExpired(now);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiredAt != null && expiredAt.isBefore(now);
    }

    public UserCoupon use(String orderNo, LocalDateTime now) {
        if (status == CouponStatus.USED) {
            if (orderNo.equals(this.orderNo)) {
                return this;
            }
            throw new BusinessException(DiscountErrorCode.COUPON_ALREADY_USED);
        }
        if (!isUsable(now)) {
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE);
        }
        return new UserCoupon(id, userId, templateId, CouponStatus.USED, orderNo,
                claimedAt, now, expiredAt);
    }

    public UserCoupon release() {
        return new UserCoupon(id, userId, templateId, CouponStatus.UNUSED, null,
                claimedAt, null, expiredAt);
    }

    public UserCoupon expire() {
        return new UserCoupon(id, userId, templateId, CouponStatus.EXPIRED, orderNo,
                claimedAt, usedAt, expiredAt);
    }

    public UserCoupon withId(Long newId) {
        return new UserCoupon(newId, userId, templateId, status, orderNo, claimedAt, usedAt, expiredAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }
}
