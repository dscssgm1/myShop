package com.four_person.discount.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.dto.PromotionRequest;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.domain.model.DiscountRule;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.Scope;
import com.four_person.discount.domain.repository.PromotionRepository;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.RuleType;
import com.four_person.discount.enums.ScopeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionApplicationService {

    private final PromotionRepository promotionRepository;

    public List<PromotionVO> list(String status) {
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> promotions;
        if (!StringUtils.hasText(status) || "active".equalsIgnoreCase(status)) {
            promotions = promotionRepository.findActive(now);
        } else if ("all".equalsIgnoreCase(status)) {
            promotions = promotionRepository.findAll();
        } else {
            String target = status.toUpperCase();
            promotions = promotionRepository.findAll().stream()
                    .filter(promotion -> target.equals(promotion.getStatus()))
                    .toList();
        }
        return promotions.stream().map(this::toVO).toList();
    }

    public PromotionVO create(PromotionRequest request) {
        Promotion promotion = toDomain(null, request);
        return toVO(promotionRepository.save(promotion));
    }

    public PromotionVO update(Long id, PromotionRequest request) {
        promotionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.PROMOTION_NOT_FOUND));
        return toVO(promotionRepository.save(toDomain(id, request)));
    }

    public void end(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.PROMOTION_NOT_FOUND));
        promotionRepository.save(promotion.ended());
    }

    private Promotion toDomain(Long id, PromotionRequest request) {
        RuleType ruleType;
        try {
            ruleType = RuleType.valueOf(request.getRuleType().trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "不支持的优惠规则类型");
        }
        Money threshold = request.getThresholdAmount() == null ? Money.zero() : Money.of(request.getThresholdAmount());
        DiscountRule rule;
        if (ruleType == RuleType.FULL_REDUCTION) {
            rule = new DiscountRule(ruleType, threshold,
                    request.getPerEvery() == null ? 0 : request.getPerEvery(),
                    request.getDiscountAmount() == null ? Money.zero() : Money.of(request.getDiscountAmount()),
                    null, null);
        } else {
            rule = new DiscountRule(ruleType, threshold, 0, Money.zero(), request.getDiscountRate(),
                    request.getMaxDiscount() == null ? null : Money.of(request.getMaxDiscount()));
        }
        ScopeType scopeType;
        try {
            scopeType = StringUtils.hasText(request.getScopeType())
                    ? ScopeType.valueOf(request.getScopeType().trim().toUpperCase()) : ScopeType.ALL;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "不支持的适用范围");
        }
        Set<Long> scopeIds = request.getScopeIds() == null ? Set.of() : Set.copyOf(request.getScopeIds());
        return new Promotion(id, request.getTitle(), request.getType(), rule, new Scope(scopeType, scopeIds),
                Boolean.TRUE.equals(request.getCouponStackable()), request.getStartTime(), request.getEndTime(),
                request.getStatus(), request.getDescription(), request.getColor());
    }

    private PromotionVO toVO(Promotion promotion) {
        PromotionVO vo = new PromotionVO();
        vo.setId(promotion.getId());
        vo.setTitle(promotion.getTitle());
        vo.setType(promotion.getType());
        vo.setRuleType(promotion.getRule().type().name());
        vo.setThresholdAmount(promotion.getRule().thresholdAmount().amount());
        vo.setPerEvery(promotion.getRule().perEvery());
        vo.setDiscountAmount(promotion.getRule().discountAmount().amount());
        vo.setDiscountRate(promotion.getRule().discountRate());
        vo.setMaxDiscount(promotion.getRule().maxDiscount() == null
                ? null : promotion.getRule().maxDiscount().amount());
        vo.setScopeType(promotion.getScope().type().name());
        vo.setScopeIds(promotion.getScope().ids().stream().sorted().collect(Collectors.toList()));
        vo.setCouponStackable(promotion.isCouponStackable());
        vo.setStartTime(promotion.getStartTime());
        vo.setEndTime(promotion.getEndTime());
        vo.setStatus(promotion.getStatus());
        vo.setStatusText(Promotion.STATUS_ACTIVE.equals(promotion.getStatus()) ? "进行中" : "已结束");
        vo.setDescription(promotion.getDescription());
        vo.setColor(promotion.getColor());
        return vo;
    }
}
