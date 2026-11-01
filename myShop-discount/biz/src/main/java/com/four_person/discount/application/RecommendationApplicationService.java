package com.four_person.discount.application;

import com.four_person.discount.api.dto.RecommendationVO;
import com.four_person.discount.application.port.OrderQueryPort;
import com.four_person.discount.application.port.ProductQueryPort;
import com.four_person.discount.domain.model.ProductSnapshot;
import com.four_person.discount.domain.service.RecommendationScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationApplicationService {

    private final OrderQueryPort orderQueryPort;
    private final ProductQueryPort productQueryPort;
    private final RecommendationScoringService scoringService;

    public List<RecommendationVO> recommend(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        try {
            List<Long> purchasedIds = orderQueryPort.purchasedProductIds(userId);
            List<ProductSnapshot> purchased = productQueryPort.batch(purchasedIds);
            List<ProductSnapshot> candidates = productQueryPort.candidates(Math.max(size * 3, 50));
            return scoringService.score(purchased, candidates, size).stream()
                    .map(this::toVO)
                    .toList();
        } catch (Exception ex) {
            log.warn("个性化推荐失败，降级为热门推荐: {}", ex.getMessage());
            return hotFallback(size);
        }
    }

    private List<RecommendationVO> hotFallback(int size) {
        try {
            List<ProductSnapshot> candidates = productQueryPort.candidates(Math.max(size * 2, 20));
            return scoringService.score(List.of(), candidates, size).stream()
                    .map(this::toVO)
                    .toList();
        } catch (Exception ex) {
            log.warn("热门推荐降级失败: {}", ex.getMessage());
            return List.of();
        }
    }

    private RecommendationVO toVO(RecommendationScoringService.ScoredProduct scored) {
        ProductSnapshot product = scored.product();
        RecommendationVO vo = new RecommendationVO();
        vo.setProductId(product.id());
        vo.setName(product.name());
        vo.setPrice(product.price().amount());
        vo.setEmoji(product.emoji());
        vo.setColor(product.color());
        vo.setShopName(product.shopName());
        vo.setCategoryId(product.categoryId());
        vo.setCategoryName(product.categoryName());
        vo.setSales(product.sales());
        vo.setRating(product.rating());
        vo.setScore(scored.score());
        vo.setReason(scored.reason());
        return vo;
    }
}
