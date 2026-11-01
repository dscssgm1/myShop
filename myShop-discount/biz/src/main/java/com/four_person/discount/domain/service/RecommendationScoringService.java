package com.four_person.discount.domain.service;

import com.four_person.discount.domain.model.ProductSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于内容（分类 / 标签 / 销量 / 评分）的个性化推荐评分领域服务。
 */
@Service
public class RecommendationScoringService {

    private static final double WEIGHT_CATEGORY = 0.5;
    private static final double WEIGHT_TAG = 0.3;
    private static final double WEIGHT_SALES = 0.2;
    private static final double WEIGHT_RATING = 0.05;

    public List<ScoredProduct> score(List<ProductSnapshot> purchased,
                                     List<ProductSnapshot> candidates,
                                     int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<ProductSnapshot> history = purchased == null ? List.of() : purchased;
        Set<Long> purchasedIds = new HashSet<>();
        Map<Long, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> tagCounts = new HashMap<>();
        for (ProductSnapshot product : history) {
            purchasedIds.add(product.id());
            if (product.categoryId() != null) {
                categoryCounts.merge(product.categoryId(), 1, Integer::sum);
            }
            product.tags().forEach(tag -> tagCounts.merge(tag, 1, Integer::sum));
        }

        int maxCategoryCount = categoryCounts.values().stream().max(Integer::compareTo).orElse(1);
        int maxTagCount = tagCounts.values().stream().max(Integer::compareTo).orElse(1);
        int maxSales = candidates.stream()
                .map(ProductSnapshot::sales)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(1);

        List<ScoredProduct> result = new ArrayList<>();
        for (ProductSnapshot candidate : candidates) {
            if (purchasedIds.contains(candidate.id())) {
                continue;
            }
            double categoryScore = 0;
            double tagScore = 0;
            if (!history.isEmpty()) {
                categoryScore = categoryCounts.getOrDefault(candidate.categoryId(), 0) / (double) maxCategoryCount;
                if (!candidate.tags().isEmpty()) {
                    int matched = candidate.tags().stream().mapToInt(tag -> tagCounts.getOrDefault(tag, 0)).sum();
                    tagScore = Math.min(matched / (double) (maxTagCount * candidate.tags().size()), 1.0);
                }
            }
            double salesScore = candidate.sales() == null || maxSales == 0
                    ? 0 : candidate.sales() / (double) maxSales;
            double ratingScore = candidate.rating() == null ? 0 : candidate.rating().doubleValue() / 5.0;

            double score = WEIGHT_CATEGORY * categoryScore
                    + WEIGHT_TAG * tagScore
                    + WEIGHT_SALES * salesScore
                    + WEIGHT_RATING * ratingScore;
            result.add(new ScoredProduct(candidate,
                    BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                    reason(candidate, categoryScore, tagScore)));
        }

        result.sort(Comparator
                .comparing(ScoredProduct::score, Comparator.reverseOrder())
                .thenComparing(item -> item.product().sales() == null ? 0 : item.product().sales(),
                        Comparator.reverseOrder())
                .thenComparing(item -> item.product().id()));
        return result.stream().limit(Math.min(limit, result.size())).toList();
    }

    private String reason(ProductSnapshot candidate, double categoryScore, double tagScore) {
        if (categoryScore > 0 && candidate.categoryName() != null) {
            return "你常买的" + candidate.categoryName();
        }
        if (tagScore > 0) {
            return "符合你的偏好";
        }
        return "热销推荐";
    }

    public record ScoredProduct(ProductSnapshot product, BigDecimal score, String reason) {
    }
}
