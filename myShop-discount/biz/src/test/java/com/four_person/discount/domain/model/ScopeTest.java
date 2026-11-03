package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.ScopeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 适用范围边界：空值兜底、分类/商品/店铺匹配、小计只算命中行。
 */
class ScopeTest {

    private OrderLine line(Long productId, Long categoryId, Long shopId, String price, int qty) {
        return new OrderLine(productId, categoryId, shopId, Money.of(price), qty);
    }

    @Test
    void shouldDefaultNullTypeToAllAndNullIdsToEmpty() {
        Scope scope = new Scope(null, null);
        assertThat(scope.type()).isEqualTo(ScopeType.ALL);
        assertThat(scope.ids()).isEmpty();
    }

    @Test
    void allScopeMatchesAnyLineIncludingNullFields() {
        Scope scope = Scope.all();
        assertThat(scope.matches(line(1L, null, null, "10.00", 1), 0L)).isTrue();
        assertThat(scope.matches(line(1L, 7L, 100L, "10.00", 1), 900L)).isTrue();
    }

    @Test
    void categoryScopeRequiresNonNullMatchingCategory() {
        Scope scope = new Scope(ScopeType.CATEGORY, Set.of(7L));
        assertThat(scope.matches(line(1L, 7L, 100L, "10.00", 1), 0L)).isTrue();
        assertThat(scope.matches(line(1L, 8L, 100L, "10.00", 1), 0L)).isFalse();
        assertThat(scope.matches(line(1L, null, 100L, "10.00", 1), 0L)).isFalse();
    }

    @Test
    void productScopeMatchesByProductIdOnly() {
        Scope scope = new Scope(ScopeType.PRODUCT, Set.of(1L, 2L));
        assertThat(scope.matches(line(1L, null, null, "10.00", 1), 0L)).isTrue();
        assertThat(scope.matches(line(3L, 7L, 100L, "10.00", 1), 0L)).isFalse();
    }

    @Test
    void shopScopeComparesWithOwnerShopId() {
        Scope scope = Scope.shop();
        assertThat(scope.matches(line(1L, 7L, 100L, "10.00", 1), 100L)).isTrue();
        assertThat(scope.matches(line(1L, 7L, 200L, "10.00", 1), 100L)).isFalse();
        assertThat(scope.matches(line(1L, 7L, 100L, "10.00", 1), null)).isFalse();
    }

    @Test
    void shopScopeDropsProvidedIds() {
        // 店铺 id 由聚合提供，scope_ids 强制置空
        Scope scope = new Scope(ScopeType.SHOP, Set.of(1L, 2L));
        assertThat(scope.ids()).isEmpty();
        assertThat(scope.idsText()).isEmpty();
    }

    @Test
    void nonAllScopeWithEmptyIdsThrows() {
        assertThatThrownBy(() -> new Scope(ScopeType.CATEGORY, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("指定范围必须配置范围 ID");
        assertThatThrownBy(() -> Scope.of(ScopeType.PRODUCT, Set.of()))
                .isInstanceOf(BusinessException.class);
        // ALL 允许空
        assertThat(new Scope(ScopeType.ALL, Set.of()).type()).isEqualTo(ScopeType.ALL);
    }

    @Test
    void subtotalOnlyCountsMatchedLines() {
        Scope scope = new Scope(ScopeType.CATEGORY, Set.of(7L));
        Money subtotal = scope.subtotalOf(List.of(
                line(1L, 7L, 100L, "33.33", 3),   // 命中 99.99
                line(2L, 8L, 100L, "100.00", 1),  // 未命中
                line(3L, 7L, 200L, "0.01", 1)     // 命中 0.01
        ), 0L);
        assertThat(subtotal.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void subtotalOfNothingMatchedIsZero() {
        Scope scope = new Scope(ScopeType.PRODUCT, Set.of(99L));
        assertThat(scope.subtotalOf(List.of(line(1L, 1L, 1L, "100.00", 1)), 0L).amount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void idsTextJoinsWithComma() {
        assertThat(new Scope(ScopeType.CATEGORY, Set.of(7L)).idsText()).isEqualTo("7");
    }
}
