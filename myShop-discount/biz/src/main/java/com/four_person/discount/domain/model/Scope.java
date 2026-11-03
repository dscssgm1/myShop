package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.ScopeType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 适用范围值对象。
 *
 * <p>SHOP 范围表示「归属店铺的全部商品」：归属 shopId 由持有该范围的聚合
 * （活动/券模板）提供，本值对象只负责按行匹配。</p>
 */
public record Scope(ScopeType type, Set<Long> ids) {

    public Scope {
        type = type == null ? ScopeType.ALL : type;
        ids = ids == null ? Set.of() : Set.copyOf(ids);
        if (type == ScopeType.SHOP) {
            ids = Set.of(); // 店铺 id 取聚合的归属 shopId，不落在 scope_ids
        } else if (type != ScopeType.ALL && ids.isEmpty()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "指定范围必须配置范围 ID");
        }
    }

    public static Scope all() {
        return new Scope(ScopeType.ALL, Set.of());
    }

    public static Scope of(ScopeType type, Set<Long> ids) {
        return new Scope(type, ids);
    }

    /** 归属店铺的全部商品范围 */
    public static Scope shop() {
        return new Scope(ScopeType.SHOP, Set.of());
    }

    public boolean matches(OrderLine line, Long ownerShopId) {
        return switch (type) {
            case ALL -> true;
            case CATEGORY -> line.categoryId() != null && ids.contains(line.categoryId());
            case PRODUCT -> ids.contains(line.productId());
            case SHOP -> ownerShopId != null && ownerShopId.equals(line.shopId());
        };
    }

    public Money subtotalOf(List<OrderLine> lines, Long ownerShopId) {
        Money total = Money.zero();
        for (OrderLine line : lines) {
            if (matches(line, ownerShopId)) {
                total = total.add(line.subtotal());
            }
        }
        return total;
    }

    public String idsText() {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
