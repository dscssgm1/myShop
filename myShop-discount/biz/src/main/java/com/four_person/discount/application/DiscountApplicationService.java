package com.four_person.discount.application;

import com.four_person.discount.api.dto.DiscountItem;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.discount.domain.model.CouponOffer;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.DiscountSelection;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.UserCoupon;
import com.four_person.discount.domain.repository.CouponTemplateRepository;
import com.four_person.discount.domain.repository.PromotionRepository;
import com.four_person.discount.domain.repository.UserCouponRepository;
import com.four_person.discount.domain.service.DiscountPricingService;
import com.four_person.discount.enums.CouponStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountApplicationService {

    private final PromotionRepository promotionRepository;
    private final CouponTemplateRepository templateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DiscountPricingService discountPricingService;

    @Transactional(readOnly = true)
    public DiscountResultVO calculate(Long userId, Long couponId, List<DiscountItem> items) {
        LocalDateTime now = LocalDateTime.now();
        List<OrderLine> lines = items.stream()
                .map(item -> new OrderLine(item.getProductId(), item.getCategoryId(),
                        Money.of(item.getPrice()), item.getQuantity()))
                .toList();
        List<Promotion> promotions = promotionRepository.findActive(now);
        List<CouponOffer> offers = offers(userId, now);

        DiscountSelection selection = discountPricingService.calculate(
                lines, promotions, offers, couponId, now);

        DiscountResultVO vo = new DiscountResultVO();
        vo.setOriginalAmount(selection.originalAmount().amount());
        vo.setPromotionDiscount(selection.promotionDiscount().amount());
        vo.setCouponDiscount(selection.couponDiscount().amount());
        vo.setTotalDiscount(selection.totalDiscount().amount());
        vo.setPayAmount(selection.payAmount().amount());
        vo.setAppliedPromotionId(selection.promotionId());
        vo.setAppliedPromotionTitle(selection.promotionTitle());
        vo.setPromotionCouponStackable(selection.promotionCouponStackable());
        vo.setAppliedCouponId(selection.couponId());
        vo.setAppliedCouponTitle(selection.couponTitle());
        return vo;
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
}
