package com.four_person.discount.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.discount.domain.model.DiscountRule;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.Scope;
import com.four_person.discount.domain.repository.PromotionRepository;
import com.four_person.discount.enums.RuleType;
import com.four_person.discount.enums.ScopeType;
import com.four_person.discount.infrastructure.persistence.mapper.PromotionMapper;
import com.four_person.discount.infrastructure.persistence.po.PromotionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PromotionRepositoryImpl implements PromotionRepository {

    private final PromotionMapper promotionMapper;

    @Override
    public List<Promotion> findActive(LocalDateTime now) {
        return promotionMapper.selectList(Wrappers.<PromotionPO>lambdaQuery()
                        .eq(PromotionPO::getStatus, Promotion.STATUS_ACTIVE)
                        .and(w -> w.isNull(PromotionPO::getStartTime).or().le(PromotionPO::getStartTime, now))
                        .and(w -> w.isNull(PromotionPO::getEndTime).or().ge(PromotionPO::getEndTime, now))
                        .orderByAsc(PromotionPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Promotion> findAll() {
        return promotionMapper.selectList(Wrappers.<PromotionPO>lambdaQuery()
                        .orderByAsc(PromotionPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Promotion> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return promotionMapper.selectList(Wrappers.<PromotionPO>lambdaQuery()
                        .in(PromotionPO::getId, ids)
                        .orderByAsc(PromotionPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Promotion> findByShop(Long shopId) {
        return promotionMapper.selectList(Wrappers.<PromotionPO>lambdaQuery()
                        .eq(PromotionPO::getShopId, shopId)
                        .orderByDesc(PromotionPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Promotion> findById(Long id) {
        return Optional.ofNullable(promotionMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Promotion save(Promotion promotion) {
        PromotionPO po = toPO(promotion);
        if (po.getId() == null) {
            promotionMapper.insert(po);
            return promotion.withId(po.getId());
        }
        promotionMapper.updateById(po);
        return promotion;
    }

    private Promotion toDomain(PromotionPO po) {
        DiscountRule rule = new DiscountRule(
                RuleType.valueOf(po.getRuleType()),
                Money.of(po.getThresholdAmount()),
                po.getPerEvery() == null ? 0 : po.getPerEvery(),
                Money.of(po.getDiscountAmount()),
                po.getDiscountRate(),
                po.getMaxDiscount() == null ? null : Money.of(po.getMaxDiscount()));
        Scope scope = new Scope(parseScope(po.getScopeType()), parseIds(po.getScopeIds()));
        return new Promotion(po.getId(), po.getShopId() == null ? 0 : po.getShopId(),
                po.getTitle(), po.getType(), rule, scope,
                Boolean.TRUE.equals(po.getCouponStackable()),
                Boolean.TRUE.equals(po.getEnrollRequired()), po.getPlatformBearRate(),
                po.getStartTime(), po.getEndTime(),
                po.getStatus(), po.getDescription(), po.getColor());
    }

    private PromotionPO toPO(Promotion promotion) {
        PromotionPO po = new PromotionPO();
        po.setId(promotion.getId());
        po.setShopId(promotion.getShopId());
        po.setTitle(promotion.getTitle());
        po.setType(promotion.getType());
        po.setRuleType(promotion.getRule().type().name());
        po.setThresholdAmount(promotion.getRule().thresholdAmount().amount());
        po.setPerEvery(promotion.getRule().perEvery());
        po.setDiscountAmount(promotion.getRule().discountAmount().amount());
        po.setDiscountRate(promotion.getRule().discountRate());
        po.setMaxDiscount(promotion.getRule().maxDiscount() == null
                ? null : promotion.getRule().maxDiscount().amount());
        po.setScopeType(promotion.getScope().type().name());
        po.setScopeIds(promotion.getScope().idsText());
        po.setCouponStackable(promotion.isCouponStackable());
        po.setEnrollRequired(promotion.isEnrollRequired());
        po.setPlatformBearRate(promotion.getPlatformBearRate().signum() > 0
                ? promotion.getPlatformBearRate() : null);
        po.setStartTime(promotion.getStartTime());
        po.setEndTime(promotion.getEndTime());
        po.setStatus(promotion.getStatus());
        po.setDescription(promotion.getDescription());
        po.setColor(promotion.getColor());
        return po;
    }

    private ScopeType parseScope(String value) {
        return StringUtils.hasText(value) ? ScopeType.valueOf(value) : ScopeType.ALL;
    }

    private Set<Long> parseIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return Set.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }
}
