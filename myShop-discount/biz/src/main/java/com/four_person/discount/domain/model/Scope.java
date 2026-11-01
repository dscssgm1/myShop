package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.ScopeType;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 适用范围值对象。
 */
public record Scope(ScopeType type, Set<Long> ids) {

    public Scope {
        type = type == null ? ScopeType.ALL : type;
        ids = ids == null ? Set.of() : Set.copyOf(ids);
        if (type != ScopeType.ALL && ids.isEmpty()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "指定范围必须配置范围 ID");
        }
    }

    public static Scope all() {
        return new Scope(ScopeType.ALL, Set.of());
    }

    public static Scope of(ScopeType type, Set<Long> ids) {
        return new Scope(type, ids);
    }

    public boolean matches(OrderLine line) {
        return switch (type) {
            case ALL -> true;
            case CATEGORY -> line.categoryId() != null && ids.contains(line.categoryId());
            case PRODUCT -> ids.contains(line.productId());
        };
    }

    public Money subtotalOf(java.util.List<OrderLine> lines) {
        Money total = Money.zero();
        for (OrderLine line : lines) {
            if (matches(line)) {
                total = total.add(line.subtotal());
            }
        }
        return total;
    }

    public String idsText() {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
