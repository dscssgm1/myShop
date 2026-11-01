package com.four_person.discount.domain.service;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.domain.model.CouponOffer;
import com.four_person.discount.domain.model.DiscountSelection;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.enums.DiscountErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 折扣计算领域服务。
 * <p>
 * 规则：一单最多一个活动 + 一张优惠券；活动需显式允许叠加时才可与优惠券同时使用；
 * 在「仅活动」「仅优惠券」「活动+优惠券」中取总优惠最大者。
 */
@Service
public class DiscountPricingService {

    public DiscountSelection calculate(List<OrderLine> lines,
                                       List<Promotion> promotions,
                                       List<CouponOffer> offers,
                                       Long selectedCouponId,
                                       LocalDateTime now) {
        Money original = lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero(), Money::add);

        Promotion bestPromotion = null;
        Money bestPromotionDiscount = Money.zero();
        for (Promotion promotion : promotions) {
            if (promotion == null || !promotion.isActiveAt(now)) {
                continue;
            }
            Money discount = promotion.discountFor(lines);
            if (!discount.isPositive()) {
                continue;
            }
            if (bestPromotion == null
                    || discount.compareTo(bestPromotionDiscount) > 0
                    || (discount.compareTo(bestPromotionDiscount) == 0 && smallerId(promotion.getId(), bestPromotion.getId()))) {
                bestPromotion = promotion;
                bestPromotionDiscount = discount;
            }
        }

        CouponOffer bestOffer = null;
        Money bestCouponDiscount = Money.zero();
        for (CouponOffer offer : offers) {
            if (offer == null || !offer.isUsable(now)) {
                continue;
            }
            Money discount = offer.discountFor(lines);
            if (!discount.isPositive()) {
                continue;
            }
            if (bestOffer == null
                    || discount.compareTo(bestCouponDiscount) > 0
                    || (discount.compareTo(bestCouponDiscount) == 0
                        && offer.coupon().getId() < bestOffer.coupon().getId())) {
                bestOffer = offer;
                bestCouponDiscount = discount;
            }
        }

        if (selectedCouponId != null) {
            CouponOffer selected = offers.stream()
                    .filter(offer -> selectedCouponId.equals(offer.coupon().getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE));
            if (!selected.isUsable(now)) {
                throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE);
            }
            Money discount = selected.discountFor(lines);
            if (!discount.isPositive()) {
                throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE, "优惠券不满足使用条件");
            }
            bestOffer = selected;
            bestCouponDiscount = discount;
        }

        Candidate selected = selectCandidate(original, bestPromotion, bestPromotionDiscount,
                bestOffer, bestCouponDiscount);

        return new DiscountSelection(
                original,
                selected.promotionDiscount,
                selected.couponDiscount,
                selected.promotion == null ? null : selected.promotion.getId(),
                selected.promotion == null ? null : selected.promotion.getTitle(),
                selected.promotion != null && selected.promotion.isCouponStackable(),
                selected.offer == null || !selected.couponDiscount.isPositive() ? null : selected.offer.coupon().getId(),
                selected.offer == null || !selected.couponDiscount.isPositive() ? null : selected.offer.template().getTitle());
    }

    private Candidate selectCandidate(Money original,
                                      Promotion promotion,
                                      Money promotionDiscount,
                                      CouponOffer offer,
                                      Money couponDiscount) {
        Candidate best = new Candidate(null, Money.zero(), null, Money.zero(), 0);
        best = better(best, cap(original, promotion, promotionDiscount, null, Money.zero(), 2));
        best = better(best, cap(original, null, Money.zero(), offer, couponDiscount, 1));
        if (promotion != null && offer != null && promotion.isCouponStackable()) {
            best = better(best, cap(original, promotion, promotionDiscount, offer, couponDiscount, 3));
        }
        return best;
    }

    private Candidate cap(Money original,
                          Promotion promotion,
                          Money promotionDiscount,
                          CouponOffer offer,
                          Money couponDiscount,
                          int preference) {
        Money total = promotionDiscount.add(couponDiscount);
        if (total.compareTo(original) > 0) {
            couponDiscount = original.subtract(promotionDiscount);
            if (!couponDiscount.isPositive()) {
                offer = null;
                couponDiscount = Money.zero();
            }
        }
        return new Candidate(promotion, promotionDiscount, offer, couponDiscount, preference);
    }

    private Candidate better(Candidate current, Candidate candidate) {
        Money currentTotal = current.promotionDiscount.add(current.couponDiscount);
        Money candidateTotal = candidate.promotionDiscount.add(candidate.couponDiscount);
        int compare = candidateTotal.compareTo(currentTotal);
        if (compare > 0 || (compare == 0 && candidate.preference > current.preference)) {
            return candidate;
        }
        return current;
    }

    private boolean smallerId(Long candidate, Long current) {
        return candidate != null && current != null && candidate < current;
    }

    private record Candidate(Promotion promotion,
                             Money promotionDiscount,
                             CouponOffer offer,
                             Money couponDiscount,
                             int preference) {
    }
}
