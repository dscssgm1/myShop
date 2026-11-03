package com.four_person.discount.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;
import com.four_person.discount.api.dto.PromotionRequest;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.application.port.ProductQueryPort;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.discount.domain.model.DiscountRule;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.ProductSnapshot;
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
    private final ShopQueryPort shopQueryPort;
    private final ProductQueryPort productQueryPort;

    /** 平台侧活动列表（平台活动 + 商家活动均可查看） */
    public List<PromotionVO> list(String status) {
        return promotionRepository.findAll().stream()
                .filter(promotion -> matchesStatus(promotion, status))
                .map(this::toVO)
                .toList();
    }

    /** 商家侧自己的活动列表（含已结束） */
    public List<PromotionVO> listByShop(Long shopId) {
        return promotionRepository.findByShop(shopId).stream().map(this::toVO).toList();
    }

    /** 平台发布活动：可勾选是否需要商家报名、平台承担比例 */
    public PromotionVO create(PromotionRequest request) {
        Promotion promotion = toDomain(null, 0L, request);
        return toVO(promotionRepository.save(promotion));
    }

    public PromotionVO update(Long id, PromotionRequest request) {
        Promotion existing = load(id);
        if (existing.isMerchantActivity()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "商家自建活动请在商家端维护");
        }
        return toVO(promotionRepository.save(toDomain(id, 0L, request)));
    }

    public void end(Long id) {
        Promotion existing = load(id);
        if (existing.isMerchantActivity()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "商家自建活动请在商家端维护");
        }
        promotionRepository.save(existing.ended());
    }

    /** 商家自建限时活动：发布即生效，范围限本店，费用商家全额承担 */
    public PromotionVO createForShop(Long shopId, PromotionRequest request) {
        Promotion promotion = toDomain(null, shopId, request);
        return toVO(promotionRepository.save(promotion));
    }

    public PromotionVO updateForShop(Long shopId, Long id, PromotionRequest request) {
        requireOwner(load(id), shopId);
        return toVO(promotionRepository.save(toDomain(id, shopId, request)));
    }

    public void endForShop(Long shopId, Long id) {
        Promotion existing = load(id);
        requireOwner(existing, shopId);
        promotionRepository.save(existing.ended());
    }

    private Promotion load(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.PROMOTION_NOT_FOUND));
    }

    private void requireOwner(Promotion promotion, Long shopId) {
        if (promotion.getShopId() == null || !promotion.getShopId().equals(shopId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作其他店铺的活动");
        }
    }

    private boolean matchesStatus(Promotion promotion, String status) {
        if (!StringUtils.hasText(status) || "active".equalsIgnoreCase(status)) {
            return Promotion.STATUS_ACTIVE.equals(promotion.getStatus());
        }
        if ("all".equalsIgnoreCase(status)) {
            return true;
        }
        return status.toUpperCase().equals(promotion.getStatus());
    }

    private Promotion toDomain(Long id, Long ownerShopId, PromotionRequest request) {
        RuleType ruleType = parseRuleType(request.getRuleType());
        ScopeType scopeType = parseScopeType(request.getScopeType(), ownerShopId);
        Set<Long> scopeIds = request.getScopeIds() == null ? Set.of() : Set.copyOf(request.getScopeIds());
        if (ownerShopId > 0 && scopeType == ScopeType.PRODUCT) {
            scopeIds = requireOwnedProducts(ownerShopId, scopeIds);
        }
        boolean enrollRequired = ownerShopId == 0 && Boolean.TRUE.equals(request.getEnrollRequired());
        return new Promotion(id, ownerShopId, request.getTitle(), request.getType(), rule(ruleType, request),
                new Scope(scopeType, scopeIds), Boolean.TRUE.equals(request.getCouponStackable()),
                enrollRequired, request.getPlatformBearRate(),
                request.getStartTime(), request.getEndTime(), request.getStatus(),
                request.getDescription(), request.getColor());
    }

    /** 商家圈定指定商品时校验商品归属，防止把他店商品挂进自家活动 */
    private Set<Long> requireOwnedProducts(Long shopId, Set<Long> productIds) {
        if (productIds.isEmpty()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "指定商品范围不能为空");
        }
        List<Long> owned = productQueryPort.batch(List.copyOf(productIds)).stream()
                .filter(product -> shopId.equals(product.shopId()))
                .map(ProductSnapshot::id)
                .toList();
        if (owned.size() != productIds.size()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "活动商品必须属于当前店铺");
        }
        return productIds;
    }

    private RuleType parseRuleType(String raw) {
        try {
            return RuleType.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "不支持的优惠规则类型");
        }
    }

    private ScopeType parseScopeType(String raw, Long ownerShopId) {
        ScopeType scopeType;
        try {
            scopeType = StringUtils.hasText(raw)
                    ? ScopeType.valueOf(raw.trim().toUpperCase()) : ScopeType.ALL;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "不支持的适用范围");
        }
        // 商家活动范围兜底为本店；仅显式圈定商品时用 PRODUCT（归属在 requireOwnedProducts 校验）
        if (ownerShopId > 0 && scopeType != ScopeType.SHOP && scopeType != ScopeType.PRODUCT) {
            return ScopeType.SHOP;
        }
        if (ownerShopId == 0 && scopeType == ScopeType.SHOP) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "平台活动不能使用店铺范围");
        }
        return scopeType;
    }

    private DiscountRule rule(RuleType ruleType, PromotionRequest request) {
        Money threshold = request.getThresholdAmount() == null ? Money.zero() : Money.of(request.getThresholdAmount());
        if (ruleType == RuleType.FULL_REDUCTION) {
            return new DiscountRule(ruleType, threshold,
                    request.getPerEvery() == null ? 0 : request.getPerEvery(),
                    request.getDiscountAmount() == null ? Money.zero() : Money.of(request.getDiscountAmount()),
                    null, null);
        }
        return new DiscountRule(ruleType, threshold, 0, Money.zero(), request.getDiscountRate(),
                request.getMaxDiscount() == null ? null : Money.of(request.getMaxDiscount()));
    }

    private PromotionVO toVO(Promotion promotion) {
        PromotionVO vo = new PromotionVO();
        vo.setId(promotion.getId());
        vo.setShopId(promotion.getShopId());
        vo.setShopName(promotion.isMerchantActivity() ? shopQueryPort.shopName(promotion.getShopId()) : null);
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
        vo.setEnrollRequired(promotion.isEnrollRequired());
        vo.setPlatformBearRate(promotion.getPlatformBearRate());
        vo.setStartTime(promotion.getStartTime());
        vo.setEndTime(promotion.getEndTime());
        vo.setStatus(promotion.getStatus());
        vo.setStatusText(Promotion.STATUS_ACTIVE.equals(promotion.getStatus()) ? "进行中" : "已结束");
        vo.setDescription(promotion.getDescription());
        vo.setColor(promotion.getColor());
        return vo;
    }
}
