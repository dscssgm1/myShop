package com.four_person.discount.application;

import com.four_person.discount.api.dto.AvailableCoupon;
import com.four_person.discount.api.dto.DiscountItem;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.discount.domain.model.CouponOffer;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.DiscountSelection;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.ProductSnapshot;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.UserCoupon;
import com.four_person.discount.domain.repository.CouponTemplateRepository;
import com.four_person.discount.domain.repository.PromotionEnrollmentRepository;
import com.four_person.discount.domain.repository.PromotionRepository;
import com.four_person.discount.domain.repository.UserCouponRepository;
import com.four_person.discount.domain.service.DiscountPricingService;
import com.four_person.discount.enums.CouponStatus;
import com.four_person.discount.enums.ScopeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountApplicationService {

    private final PromotionRepository promotionRepository;
    private final PromotionEnrollmentRepository enrollmentRepository;
    private final CouponTemplateRepository templateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DiscountPricingService discountPricingService;
    private final ShopQueryPort shopQueryPort;

    /**
     * 订单算价入口：按平台/商家活动与优惠券（可多张叠加）计算最终应付与费用承担，
     * 报名制活动的范围收窄为「审核通过的商品」。
     */
    @Transactional(readOnly = true)
    public DiscountResultVO calculate(Long userId, List<Long> selectedCouponIds, List<DiscountItem> items) {
        LocalDateTime now = LocalDateTime.now();
        List<OrderLine> lines = toLines(items);
        List<Promotion> promotions = effectivePromotions(now);
        List<CouponOffer> offers = offers(userId, now);

        DiscountSelection selection = discountPricingService.calculate(
                lines, promotions, offers, selectedCouponIds, now);
        return toResultVO(selection);
    }

    /**
     * 结算页可用优惠券列表：按当前商品明细给出每张券的实际可抵扣金额
     * （不满足门槛为 0，前端禁用），多券叠加由用户自行勾选。
     */
    @Transactional(readOnly = true)
    public List<AvailableCoupon> availableCoupons(Long userId, List<DiscountItem> items) {
        if (userId == null) {
            return Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();
        List<OrderLine> lines = toLines(items);
        List<UserCoupon> coupons = userCouponRepository.findByUserIdAndStatus(userId, CouponStatus.UNUSED);
        if (coupons.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CouponTemplate> templates = templateRepository.findByIds(
                        coupons.stream().map(UserCoupon::getTemplateId).distinct().toList())
                .stream().collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
        List<AvailableCoupon> result = new ArrayList<>();
        for (UserCoupon coupon : coupons) {
            CouponTemplate template = templates.get(coupon.getTemplateId());
            if (template == null) {
                continue;
            }
            AvailableCoupon vo = new AvailableCoupon();
            vo.setUserCouponId(coupon.getId());
            vo.setTemplateId(template.getId());
            vo.setTitle(template.getTitle());
            vo.setThresholdAmount(template.getThresholdAmount().amount());
            vo.setDiscountAmount(template.getDiscountAmount().amount());
            vo.setApplicableAmount(
                    coupon.isUsable(now) ? template.discountFor(lines).amount() : BigDecimal.ZERO);
            vo.setScopeType(template.getScope().type().name());
            vo.setScopeText(scopeText(template.getScope().type()));
            vo.setShopId(template.isMerchantCoupon() ? template.getShopId() : null);
            vo.setShopName(template.isMerchantCoupon() ? shopName(template.getShopId()) : null);
            vo.setCouponStackable(template.isCouponStackable());
            vo.setStackable(template.isStackable());
            vo.setExpiredAt(coupon.getExpiredAt());
            result.add(vo);
        }
        return result;
    }

    private List<OrderLine> toLines(List<DiscountItem> items) {
        return items.stream()
                .map(item -> new OrderLine(item.getProductId(), item.getCategoryId(), item.getShopId(),
                        Money.of(item.getPrice()), item.getQuantity()))
                .toList();
    }

    /** 进行中的活动；报名制平台活动收窄为审核通过的商品范围，无通过商品则跳过 */
    private List<Promotion> effectivePromotions(LocalDateTime now) {
        List<Promotion> result = new ArrayList<>();
        for (Promotion promotion : promotionRepository.findActive(now)) {
            if (promotion.isMerchantActivity() || !promotion.isEnrollRequired()) {
                result.add(promotion);
                continue;
            }
            Set<Long> approved = Set.copyOf(enrollmentRepository.approvedProductIds(promotion.getId()));
            if (approved.isEmpty()) {
                continue;
            }
            result.add(promotion.narrowedToProducts(approved));
        }
        return result;
    }

    private List<CouponOffer> offers(Long userId, LocalDateTime now) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UserCoupon> coupons = userCouponRepository.findByUserIdAndStatus(userId, CouponStatus.UNUSED)
                .stream()
                .filter(coupon -> coupon.isUsable(now))
                .toList();
        if (coupons.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CouponTemplate> templates = templateRepository.findByIds(
                        coupons.stream().map(UserCoupon::getTemplateId).distinct().toList())
                .stream().collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
        return coupons.stream()
                .filter(coupon -> templates.containsKey(coupon.getTemplateId()))
                .map(coupon -> new CouponOffer(coupon, templates.get(coupon.getTemplateId())))
                .toList();
    }

    private DiscountResultVO toResultVO(DiscountSelection selection) {
        DiscountResultVO vo = new DiscountResultVO();
        vo.setOriginalAmount(selection.originalAmount().amount());
        vo.setPromotionDiscount(selection.promotionDiscount().amount());
        vo.setCouponDiscount(selection.couponDiscount().amount());
        vo.setTotalDiscount(selection.totalDiscount().amount());
        vo.setPayAmount(selection.payAmount().amount());
        vo.setMerchantBearAmount(selection.merchantBearAmount().amount());
        vo.setPlatformBearAmount(selection.platformBearAmount().amount());
        selection.primaryPromotion().ifPresent(promotion -> {
            vo.setAppliedPromotionId(promotion.getId());
            vo.setAppliedPromotionTitle(promotion.getTitle());
        });
        vo.setPromotionCouponStackable(selection.promotionCouponStackable());
        List<DiscountResultVO.CouponUsage> usages = new ArrayList<>();
        for (int i = 0; i < selection.coupons().size(); i++) {
            CouponOffer offer = selection.coupons().get(i);
            DiscountResultVO.CouponUsage usage = new DiscountResultVO.CouponUsage();
            usage.setCouponId(offer.coupon().getId());
            usage.setTitle(offer.template().getTitle());
            usage.setDiscount(selection.couponHits().get(i).amount());
            usages.add(usage);
        }
        vo.setAppliedCoupons(usages);
        selection.primaryCoupon().ifPresent(offer -> {
            vo.setAppliedCouponId(offer.coupon().getId());
            vo.setAppliedCouponTitle(offer.template().getTitle());
        });
        return vo;
    }

    private String shopName(Long shopId) {
        return shopQueryPort.shopName(shopId);
    }

    private String scopeText(ScopeType type) {
        return switch (type) {
            case CATEGORY -> "指定分类";
            case PRODUCT -> "指定商品";
            case SHOP -> "本店商品";
            case ALL -> "全品类";
        };
    }
}
