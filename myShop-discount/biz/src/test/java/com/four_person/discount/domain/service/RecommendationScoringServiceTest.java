package com.four_person.discount.domain.service;

import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.ProductSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScoringServiceTest {

    private final RecommendationScoringService service = new RecommendationScoringService();

    @Test
    void shouldPreferCategoriesUserBoughtBefore() {
        ProductSnapshot purchased = product(1L, 1L, "手机数码", List.of("自营"), 1000, "4.8");
        ProductSnapshot sameCategory = product(2L, 1L, "手机数码", List.of("自营"), 100, "4.6");
        ProductSnapshot otherCategory = product(3L, 4L, "食品生鲜", List.of("秒杀"), 5000, "4.9");

        List<RecommendationScoringService.ScoredProduct> result =
                service.score(List.of(purchased), List.of(sameCategory, otherCategory, purchased), 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).product().id()).isEqualTo(2L);
        assertThat(result.get(0).reason()).contains("手机数码");
        assertThat(result).extracting(item -> item.product().id()).doesNotContain(1L);
    }

    @Test
    void shouldFallbackToSalesRankingForNewUser() {
        ProductSnapshot lowSales = product(1L, 1L, "手机数码", List.of(), 50, "4.5");
        ProductSnapshot highSales = product(2L, 2L, "家用电器", List.of(), 9000, "4.6");

        List<RecommendationScoringService.ScoredProduct> result =
                service.score(List.of(), List.of(lowSales, highSales), 2);

        assertThat(result).extracting(item -> item.product().id()).containsExactly(2L, 1L);
        assertThat(result.get(0).reason()).isEqualTo("热销推荐");
    }

    @Test
    void shouldRespectLimit() {
        List<ProductSnapshot> candidates = List.of(
                product(1L, 1L, "手机数码", List.of(), 100, "4.5"),
                product(2L, 1L, "手机数码", List.of(), 200, "4.6"),
                product(3L, 1L, "手机数码", List.of(), 300, "4.7"));

        assertThat(service.score(List.of(), candidates, 1)).hasSize(1);
    }

    private ProductSnapshot product(Long id, Long categoryId, String categoryName,
                                    List<String> tags, int sales, String rating) {
        return new ProductSnapshot(id, "商品" + id, Money.of("99.00"), categoryId, categoryName,
                1L, "测试店铺", "🎁", "#fff", tags, sales, new BigDecimal(rating));
    }
}
