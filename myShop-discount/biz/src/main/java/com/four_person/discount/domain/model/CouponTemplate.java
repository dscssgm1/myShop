package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券模板聚合根。
 */
public class CouponTemplate {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    private final Long id;
    /** 0 平台券；>0 商家券（归属店铺，费用由商家承担） */
    private final Long shopId;
    private final String title;
    private final Money thresholdAmount;
    private final Money discountAmount;
    private final Scope scope;
    private final int totalStock;
    private final int claimedCount;
    private final int perUserLimit;
    private final LocalDateTime validStart;
    private final LocalDateTime validEnd;
    private final String status;

    public CouponTemplate(Long id, Long shopId, String title, Money thresholdAmount, Money discountAmount, Scope scope,
                          int totalStock, int claimedCount, int perUserLimit,
                          LocalDateTime validStart, LocalDateTime validEnd, String status) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "优惠券名称不能为空");
        }
        if (discountAmount == null || !discountAmount.isPositive()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "优惠金额必须大于 0");
        }
        if (totalStock < 1) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "发放总量必须大于 0");
        }
        if (perUserLimit < 1) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "每人限领数量必须大于 0");
        }
        long owner = shopId == null ? 0 : shopId;
        Scope effectiveScope = scope == null ? Scope.all() : scope;
        if (owner > 0 && effectiveScope.type() != com.four_person.discount.enums.ScopeType.SHOP
                && effectiveScope.type() != com.four_person.discount.enums.ScopeType.PRODUCT) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "商家优惠券范围只能是本店商品");
        }
        if (validStart != null && validEnd != null && validStart.isAfter(validEnd)) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "有效期开始时间不能晚于结束时间");
        }
        this.id = id;
        this.shopId = owner;
        this.title = title.trim();
        this.thresholdAmount = thresholdAmount == null ? Money.zero() : thresholdAmount;
        this.discountAmount = discountAmount;
        this.scope = effectiveScope;
        this.totalStock = totalStock;
        this.claimedCount = claimedCount;
        this.perUserLimit = perUserLimit;
        this.validStart = validStart;
        this.validEnd = validEnd;
        this.status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return STATUS_ACTIVE.equals(status)
                && (validStart == null || !now.isBefore(validStart))
                && (validEnd == null || !now.isAfter(validEnd));
    }

    public boolean canClaim(int claimedByUser, LocalDateTime now) {
        return isActiveAt(now)
                && claimedByUser < perUserLimit
                && claimedCount < totalStock;
    }

    public void ensureClaimable(int claimedByUser, LocalDateTime now) {
        if (!isActiveAt(now)) {
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE, "优惠券不在可领取时间内");
        }
        if (claimedByUser >= perUserLimit) {
            throw new BusinessException(DiscountErrorCode.CLAIM_LIMIT_REACHED);
        }
        if (claimedCount >= totalStock) {
            throw new BusinessException(DiscountErrorCode.COUPON_STOCK_EMPTY);
        }
    }

    public Money discountFor(List<OrderLine> lines) {
        Money eligible = scope.subtotalOf(lines, shopId);
        if (eligible.compareTo(thresholdAmount) < 0) {
            return Money.zero();
        }
        return discountAmount.min(eligible);
    }

    public CouponTemplate withId(Long newId) {
        return new CouponTemplate(newId, shopId, title, thresholdAmount, discountAmount, scope,
                totalStock, claimedCount, perUserLimit, validStart, validEnd, status);
    }

    public CouponTemplate withClaimedCount(int count) {
        return new CouponTemplate(id, shopId, title, thresholdAmount, discountAmount, scope,
                totalStock, count, perUserLimit, validStart, validEnd, status);
    }

    public CouponTemplate ended() {
        return new CouponTemplate(id, shopId, title, thresholdAmount, discountAmount, scope,
                totalStock, claimedCount, perUserLimit, validStart, validEnd, STATUS_ENDED);
    }

    public Long getId() {
        return id;
    }

    public Long getShopId() {
        return shopId;
    }

    public boolean isMerchantCoupon() {
        return shopId > 0;
    }

    public String getTitle() {
        return title;
    }

    public Money getThresholdAmount() {
        return thresholdAmount;
    }

    public Money getDiscountAmount() {
        return discountAmount;
    }

    public Scope getScope() {
        return scope;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getClaimedCount() {
        return claimedCount;
    }

    public int getRemaining() {
        return Math.max(totalStock - claimedCount, 0);
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public LocalDateTime getValidStart() {
        return validStart;
    }

    public LocalDateTime getValidEnd() {
        return validEnd;
    }

    public String getStatus() {
        return status;
    }
}
