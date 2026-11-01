package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动聚合根。
 */
public class Promotion {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    private final Long id;
    private final String title;
    private final String type;
    private final DiscountRule rule;
    private final Scope scope;
    private final boolean couponStackable;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String status;
    private final String description;
    private final String color;

    public Promotion(Long id, String title, String type, DiscountRule rule, Scope scope,
                     boolean couponStackable, LocalDateTime startTime, LocalDateTime endTime,
                     String status, String description, String color) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "活动名称不能为空");
        }
        if (rule == null) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "活动规则不能为空");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "开始时间不能晚于结束时间");
        }
        this.id = id;
        this.title = title.trim();
        this.type = type == null || type.isBlank() ? "平台大促" : type;
        this.rule = rule;
        this.scope = scope == null ? Scope.all() : scope;
        this.couponStackable = couponStackable;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
        this.description = description == null ? "" : description;
        this.color = color == null ? "" : color;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return STATUS_ACTIVE.equals(status)
                && (startTime == null || !now.isBefore(startTime))
                && (endTime == null || !now.isAfter(endTime));
    }

    public Money discountFor(List<OrderLine> lines) {
        return rule.discountFor(scope.subtotalOf(lines));
    }

    public Promotion ended() {
        return new Promotion(id, title, type, rule, scope, couponStackable,
                startTime, endTime, STATUS_ENDED, description, color);
    }

    public Promotion withId(Long newId) {
        return new Promotion(newId, title, type, rule, scope, couponStackable,
                startTime, endTime, status, description, color);
    }

    public Long getId() {
        return id;
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
