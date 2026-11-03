package com.four_person.discount.application;

import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.dto.EnrollmentVO;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.application.port.ProductQueryPort;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.ProductSnapshot;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.PromotionEnrollment;
import com.four_person.discount.domain.repository.PromotionEnrollmentRepository;
import com.four_person.discount.domain.repository.PromotionRepository;
import com.four_person.discount.enums.DiscountErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 招商活动报名：商家挑自家商品报名平台活动，平台审核通过后商品才享受活动价。
 * 报名时校验活动可报名、商品归属与活动范围；上下架状态由平台审核把关。
 */
@Service
@RequiredArgsConstructor
public class EnrollmentApplicationService {

    private final PromotionRepository promotionRepository;
    private final PromotionEnrollmentRepository enrollmentRepository;
    private final ProductQueryPort productQueryPort;
    private final ShopQueryPort shopQueryPort;

    /** 商家可报名的平台活动：报名制 + 进行中 */
    public List<PromotionVO> available(Long shopId) {
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findActive(now).stream()
                .filter(promotion -> !promotion.isMerchantActivity() && promotion.isEnrollRequired())
                .filter(promotion -> promotion.isActiveAt(now))
                .map(this::toPromotionVO)
                .toList();
    }

    /** 商家批量报名商品（同一活动同一商品一条记录；被驳回后可重新提交） */
    @Transactional(rollbackFor = Exception.class)
    public int apply(Long shopId, Long promotionId, List<Long> productIds) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.PROMOTION_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (promotion.isMerchantActivity() || !promotion.isEnrollRequired()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "该活动无需报名，全场自动生效");
        }
        if (!promotion.isActiveAt(now)) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "活动不在报名时间内");
        }
        if (productIds == null || productIds.isEmpty()) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "请选择要报名的商品");
        }
        String shopName = shopQueryPort.shopName(shopId);
        Map<Long, ProductSnapshot> products = productQueryPort.batch(productIds).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
        int applied = 0;
        for (Long productId : productIds.stream().distinct().toList()) {
            ProductSnapshot product = products.get(productId);
            if (product == null) {
                throw new BusinessException(DiscountErrorCode.INVALID_RULE, "商品不存在：" + productId);
            }
            if (!shopId.equals(product.shopId())) {
                throw new BusinessException(DiscountErrorCode.INVALID_RULE,
                        "商品「" + product.name() + "」不属于当前店铺");
            }
            OrderLine line = new OrderLine(product.id(), product.categoryId(), product.shopId(),
                    product.price(), 1);
            if (!promotion.getScope().matches(line, promotion.getShopId())) {
                throw new BusinessException(DiscountErrorCode.INVALID_RULE,
                        "商品「" + product.name() + "」不在活动范围内");
            }
            saveEnrollment(promotion.getId(), shopId, shopName, product);
            applied++;
        }
        return applied;
    }

    private void saveEnrollment(Long promotionId, Long shopId, String shopName,
                                com.four_person.discount.domain.model.ProductSnapshot product) {
        Optional<PromotionEnrollment> existing =
                enrollmentRepository.findByPromotionAndProduct(promotionId, product.id());
        if (existing.isPresent()) {
            PromotionEnrollment record = existing.get();
            if (!PromotionEnrollment.STATUS_REJECTED.equals(record.getStatus())) {
                throw new BusinessException(DiscountErrorCode.INVALID_RULE,
                        "商品「" + product.name() + "」已在报名中或已通过审核");
            }
            // 被驳回的报名允许重新提交：复位为待审核
            enrollmentRepository.save(record.reapplied());
            return;
        }
        enrollmentRepository.save(PromotionEnrollment.apply(
                promotionId, shopId, shopName, product.id(), product.name()));
    }

    /** 商家自己的报名记录 */
    public List<EnrollmentVO> mine(Long shopId) {
        return toVOs(enrollmentRepository.findByShop(shopId));
    }

    /** 平台审核列表，可按活动/店铺/状态过滤 */
    public PageResult<EnrollmentVO> page(Long promotionId, Long shopId, String status, int page, int size) {
        PageResult<PromotionEnrollment> result =
                enrollmentRepository.page(promotionId, shopId, normalizeStatus(status), page, size);
        return PageResult.of(toVOs(result.getList()),
                result.getTotal(), result.getPage(), result.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        PromotionEnrollment enrollment = loadPending(id);
        enrollmentRepository.save(enrollment.approved());
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        PromotionEnrollment enrollment = loadPending(id);
        enrollmentRepository.save(enrollment.rejected(reason));
    }

    private PromotionEnrollment loadPending(Long id) {
        PromotionEnrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.PROMOTION_NOT_FOUND,
                        "报名记录不存在"));
        if (!PromotionEnrollment.STATUS_PENDING.equals(enrollment.getStatus())) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "该报名记录已审核");
        }
        return enrollment;
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase();
    }

    private PromotionVO toPromotionVO(Promotion promotion) {
        PromotionVO vo = new PromotionVO();
        vo.setId(promotion.getId());
        vo.setShopId(promotion.getShopId());
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
        vo.setScopeIds(promotion.getScope().ids().stream().sorted().toList());
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

    private List<EnrollmentVO> toVOs(List<PromotionEnrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> titles = promotionRepository.findByIds(enrollments.stream()
                        .map(PromotionEnrollment::getPromotionId).distinct().toList())
                .stream().collect(Collectors.toMap(Promotion::getId, Promotion::getTitle));
        return enrollments.stream()
                .map(enrollment -> toVO(enrollment, titles.getOrDefault(enrollment.getPromotionId(), "")))
                .toList();
    }

    private EnrollmentVO toVO(PromotionEnrollment enrollment, String promotionTitle) {
        EnrollmentVO vo = new EnrollmentVO();
        vo.setId(enrollment.getId());
        vo.setPromotionId(enrollment.getPromotionId());
        vo.setPromotionTitle(promotionTitle);
        vo.setShopId(enrollment.getShopId());
        vo.setShopName(enrollment.getShopName());
        vo.setProductId(enrollment.getProductId());
        vo.setProductName(enrollment.getProductName());
        vo.setStatus(enrollment.getStatus());
        vo.setStatusText(statusText(enrollment.getStatus()));
        vo.setReason(enrollment.getReason());
        vo.setAppliedAt(enrollment.getAppliedAt());
        vo.setReviewedAt(enrollment.getReviewedAt());
        return vo;
    }

    private String statusText(String status) {
        return switch (status) {
            case PromotionEnrollment.STATUS_APPROVED -> "已通过";
            case PromotionEnrollment.STATUS_REJECTED -> "已驳回";
            default -> "待审核";
        };
    }
}
