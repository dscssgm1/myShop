package com.four_person.discount.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.Scope;
import com.four_person.discount.domain.repository.CouponTemplateRepository;
import com.four_person.discount.enums.ScopeType;
import com.four_person.discount.infrastructure.persistence.mapper.CouponTemplateMapper;
import com.four_person.discount.infrastructure.persistence.po.CouponTemplatePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateMapper templateMapper;

    @Override
    public List<CouponTemplate> findActive(LocalDateTime now) {
        return templateMapper.selectList(Wrappers.<CouponTemplatePO>lambdaQuery()
                        .eq(CouponTemplatePO::getStatus, CouponTemplate.STATUS_ACTIVE)
                        .and(w -> w.isNull(CouponTemplatePO::getValidStart).or().le(CouponTemplatePO::getValidStart, now))
                        .and(w -> w.isNull(CouponTemplatePO::getValidEnd).or().ge(CouponTemplatePO::getValidEnd, now))
                        .orderByAsc(CouponTemplatePO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<CouponTemplate> findByShop(Long shopId) {
        return templateMapper.selectList(Wrappers.<CouponTemplatePO>lambdaQuery()
                        .eq(CouponTemplatePO::getShopId, shopId)
                        .orderByDesc(CouponTemplatePO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return Optional.ofNullable(templateMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<CouponTemplate> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return templateMapper.selectBatchIds(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public CouponTemplate save(CouponTemplate template) {
        CouponTemplatePO po = toPO(template);
        if (po.getId() == null) {
            templateMapper.insert(po);
            return template.withId(po.getId());
        }
        templateMapper.updateById(po);
        return template;
    }

    @Override
    public boolean increaseClaimed(Long id) {
        return templateMapper.increaseClaimed(id) > 0;
    }

    private CouponTemplate toDomain(CouponTemplatePO po) {
        Scope scope = new Scope(parseScope(po.getScopeType()), parseIds(po.getScopeIds()));
        return new CouponTemplate(po.getId(), po.getShopId() == null ? 0 : po.getShopId(), po.getTitle(),
                Money.of(po.getThresholdAmount()), Money.of(po.getDiscountAmount()), scope,
                po.getTotalStock() == null ? 0 : po.getTotalStock(),
                po.getClaimedCount() == null ? 0 : po.getClaimedCount(),
                po.getPerUserLimit() == null ? 1 : po.getPerUserLimit(),
                po.getValidStart(), po.getValidEnd(), po.getStatus());
    }

    private CouponTemplatePO toPO(CouponTemplate template) {
        CouponTemplatePO po = new CouponTemplatePO();
        po.setId(template.getId());
        po.setShopId(template.getShopId());
        po.setTitle(template.getTitle());
        po.setThresholdAmount(template.getThresholdAmount().amount());
        po.setDiscountAmount(template.getDiscountAmount().amount());
        po.setScopeType(template.getScope().type().name());
        po.setScopeIds(template.getScope().idsText());
        po.setTotalStock(template.getTotalStock());
        po.setClaimedCount(template.getClaimedCount());
        po.setPerUserLimit(template.getPerUserLimit());
        po.setValidStart(template.getValidStart());
        po.setValidEnd(template.getValidEnd());
        po.setStatus(template.getStatus());
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
