package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动聚合根。
 *
 * <p>shopId = 0 为平台活动（可配置是否需商家报名）；shopId &gt; 0 为商家自建活动，
 * 范围限本店商品，发布即生效，优惠费用由商家全额承担。</p>
 */
public class Promotion {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    private final Long id;
    /** 0 平台活动；>0 商家自建活动（归属店铺） */
    private final Long shopId;
    private final String title;
    private final String type;
    private final DiscountRule rule;
    private final Scope scope;
    private final boolean couponStackable;
    /** 是否需要商家报名商品参加（仅平台活动有意义） */
    private final boolean enrollRequired;
    /** 平台承担优惠比例（%），null/0 表示商家全额承担 */
    private final BigDecimal platformBearRate;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String status;
    private final String description;
    private final String color;

    public Promotion(Long id, Long shopId, String title, String type, DiscountRule rule, Scope scope,
                     boolean couponStackable, boolean enrollRequired, BigDecimal platformBearRate,
                     LocalDateTime startTime, LocalDateTime endTime,
                     String status, String description, String color) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "活动名称不能为空");
        }
        if (rule == null) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "活动规则不能为空");
        }
        long owner = shopId == null ? 0 : shopId;
        Scope effectiveScope = scope == null ? Scope.all() : scope;
        BigDecimal bearRate = platformBearRate == null ? BigDecimal.ZERO : platformBearRate;
        if (bearRate.signum() < 0 || bearRate.compareTo(HUNDRED) > 0) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "平台承担比例必须在 0~100 之间");
        }
        if (owner > 0) {
            // 商家自建活动：范围只能圈本店，费用全部由商家承担
            if (effectiveScope.type() != com.four_person.discount.enums.ScopeType.SHOP
                    && effectiveScope.type() != com.four_person.discount.enums.ScopeType.PRODUCT) {
                throw new BusinessException(DiscountErrorCode.INVALID_RULE, "商家活动范围只能是本店商品");
            }
            bearRate = BigDecimal.ZERO;
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "开始时间不能晚于结束时间");
        }
        this.id = id;
        this.shopId = owner;
        this.title = title.trim();
        this.type = type == null || type.isBlank() ? (owner > 0 ? "店铺促销" : "平台大促") : type;
        this.rule = rule;
        this.scope = effectiveScope;
        this.couponStackable = couponStackable;
        this.enrollRequired = owner == 0 && enrollRequired;
        this.platformBearRate = bearRate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
        this.description = description == null ? "" : description;
        this.color = color == null ? "" : color;
    }

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public boolean isActiveAt(LocalDateTime now) {
        return STATUS_ACTIVE.equals(status)
                && (startTime == null || !now.isBefore(startTime))
                && (endTime == null || !now.isAfter(endTime));
    }

    public boolean isMerchantActivity() {
        return shopId > 0;
    }

    public Money discountFor(List<OrderLine> lines) {
        return rule.discountFor(scope.subtotalOf(lines, shopId));
    }

    /** 该活动在优惠 discount 下平台承担的金额 */
    public Money platformBearOf(Money discount) {
        if (!discount.isPositive() || platformBearRate.signum() <= 0) {
            return Money.zero();
        }
        return new Money(discount.amount()
                .multiply(platformBearRate)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP))
                .min(discount);
    }

    /** 该活动在优惠 discount 下商家承担的金额 */
    public Money merchantBearOf(Money discount) {
        return discount.subtract(platformBearOf(discount));
    }

    public Promotion ended() {
        return copy(STATUS_ENDED);
    }

    public Promotion withId(Long newId) {
        return copyWithId(newId, status);
    }

    /**
     * 报名制活动算价前的范围收窄：只对审核通过的商品生效。
     * productIds 为空时抛出规则异常，调用方必须先跳过无通过商品的活动。
     */
    public Promotion narrowedToProducts(java.util.Set<Long> productIds) {
        Scope narrowed = Scope.of(com.four_person.discount.enums.ScopeType.PRODUCT, productIds);
        return new Promotion(id, shopId, title, type, rule, narrowed, couponStackable,
                false, platformBearRate, startTime, endTime, status, description, color);
    }

    private Promotion copy(String newStatus) {
        return copyWithId(id, newStatus);
    }

    private Promotion copyWithId(Long newId, String newStatus) {
        return new Promotion(newId, shopId, title, type, rule, scope, couponStackable,
                enrollRequired, platformBearRate, startTime, endTime, newStatus, description, color);
    }

    public Long getId() {
        return id;
    }

    public Long getShopId() {
        return shopId;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public DiscountRule getRule() {
        return rule;
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isCouponStackable() {
        return couponStackable;
    }

    public boolean isEnrollRequired() {
        return enrollRequired;
    }

    public BigDecimal getPlatformBearRate() {
        return platformBearRate;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }
}
