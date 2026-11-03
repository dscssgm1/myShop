package com.four_person.discount.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * 一次算价的结果值对象。
 *
 * @param promotions    实际生效的活动（平台 + 商家活动可并存，行集合互不相交）
 * @param promotionHits 每个生效活动对应的优惠金额，与 promotions 一一对应
 * @param coupons       实际生效的优惠券（可多张叠加），与 couponHits 一一对应
 * @param couponHits    每张券对应的抵扣金额
 */
public record DiscountSelection(Money originalAmount,
                                List<Promotion> promotions,
                                List<Money> promotionHits,
                                List<CouponOffer> coupons,
                                List<Money> couponHits,
                                boolean promotionCouponStackable) {

    public DiscountSelection {
        promotions = promotions == null ? List.of() : List.copyOf(promotions);
        promotionHits = promotionHits == null ? List.of() : List.copyOf(promotionHits);
        coupons = coupons == null ? List.of() : List.copyOf(coupons);
        couponHits = couponHits == null ? List.of() : List.copyOf(couponHits);
    }

    public Money promotionDiscount() {
        return promotionHits.stream().reduce(Money.zero(), Money::add);
    }

    public Money couponDiscount() {
        return couponHits.stream().reduce(Money.zero(), Money::add);
    }

    public Money totalDiscount() {
        return promotionDiscount().add(couponDiscount());
    }

    public Money payAmount() {
        return originalAmount.subtract(totalDiscount()).min(originalAmount).max(Money.zero());
    }

    /** 实际生效优惠中平台承担（补贴）的金额：活动按比例、平台券全额 */
    public Money platformBearAmount() {
        Money bear = Money.zero();
        for (int i = 0; i < promotions.size(); i++) {
            bear = bear.add(promotions.get(i).platformBearOf(promotionHits.get(i)));
        }
        for (int i = 0; i < coupons.size(); i++) {
            bear = bear.add(coupons.get(i).platformBearOf(couponHits.get(i)));
        }
        return bear.min(totalDiscount());
    }

    /** 实际生效优惠中商家承担的金额 */
    public Money merchantBearAmount() {
        return totalDiscount().subtract(platformBearAmount());
    }

    /** 优惠额最大的主活动（展示/落库用），无生效活动返回 empty */
    public Optional<Promotion> primaryPromotion() {
        int index = maxIndex(promotionHits);
        return index < 0 ? Optional.empty() : Optional.of(promotions.get(index));
    }

    /** 优惠额最大的主券（展示/落库用），无生效券返回 empty */
    public Optional<CouponOffer> primaryCoupon() {
        int index = maxIndex(couponHits);
        return index < 0 ? Optional.empty() : Optional.of(coupons.get(index));
    }

    private static int maxIndex(List<Money> hits) {
        int best = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).isPositive() && (best < 0 || hits.get(i).compareTo(hits.get(best)) > 0)) {
                best = i;
            }
        }
        return best;
    }
}
