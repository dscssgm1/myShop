package com.four_person.discount.domain.service;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.domain.model.CouponOffer;
import com.four_person.discount.domain.model.DiscountSelection;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.Promotion;
import com.four_person.discount.domain.model.PromotionSettings;
import com.four_person.discount.enums.DiscountErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 折扣计算领域服务。
 *
 * <p>规则（按优先级）：</p>
 * <ol>
 *   <li>活动：一单最多一个平台活动 + 每店铺一个商家活动（各自取优惠最大者）；
 *       平台活动与商家活动命中的商品行有交集时视为冲突，二者只保留总优惠更大的一边。</li>
 *   <li>优惠券：允许叠加时一单可用多张（受全局开关与单叠上限约束）。
 *       每张券的可抵扣额相互独立（min(面额, 范围内小计)），只受「活动优惠后的剩余应付」约束，
 *       因此按单券优惠降序贪心取前 K 张即为最优组合。</li>
 *   <li>叠加：活动与优惠券同用要求全局开关开启且每个生效活动都允许叠加；
 *       不允许时在「仅活动」「仅优惠券（用户已选券或最优单券）」之间取总优惠更大者。</li>
 *   <li>封顶：活动 + 券的优惠总额永不超过商品总额（余量不足的券被截断或舍弃）。</li>
 * </ol>
 */
@Service
public class DiscountPricingService {

    /** 自动选券时参与贪心的候选上限，防止极端用户券量拖慢算价 */
    private static final int MAX_COUPON_CANDIDATES = 12;

    public DiscountSelection calculate(List<OrderLine> lines,
                                       List<Promotion> promotions,
                                       List<CouponOffer> offers,
                                       List<Long> selectedCouponIds,
                                       PromotionSettings settings,
                                       LocalDateTime now) {
        Money original = lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero(), Money::add);
        PromotionSettings effectiveSettings = settings == null ? PromotionSettings.defaults() : settings;
        if (!original.isPositive()) {
            return new DiscountSelection(original, List.of(), List.of(), List.of(), List.of(), false);
        }

        List<Promotion> appliedPromotions = selectPromotions(promotions, lines, now);
        Money promotionDiscount = appliedPromotions.stream()
                .map(promotion -> promotion.discountFor(lines))
                .reduce(Money.zero(), Money::add);

        boolean stackingAllowed = appliedPromotions.isEmpty()
                || (effectiveSettings.couponStackable()
                    && appliedPromotions.stream().allMatch(Promotion::isCouponStackable));

        List<CouponOffer> usableOffers = offers.stream()
                .filter(offer -> offer != null && offer.isUsable(now) && offer.discountFor(lines).isPositive())
                .toList();
        // 券的抵扣预算 = 商品总额 - 已生效活动优惠，保证活动+券不超过商品总额
        Money couponBudget = stackingAllowed ? original.subtract(promotionDiscount) : original;
        CouponPlan plan = selectedCouponIds == null || selectedCouponIds.isEmpty()
                ? autoCoupons(usableOffers, lines, effectiveSettings, couponBudget)
                : requestedCoupons(selectedCouponIds, usableOffers, lines, effectiveSettings, couponBudget);
        if (!stackingAllowed && promotionDiscount.isPositive()) {
            // 活动不允许叠加券：在「仅活动」与「仅券」中取更优者
            if (plan.total.compareTo(promotionDiscount) >= 0) {
                return new DiscountSelection(original, List.of(), List.of(),
                        plan.coupons, plan.hits, false);
            }
            return new DiscountSelection(original, appliedPromotions, promotionHits(appliedPromotions, lines),
                    List.of(), List.of(), false);
        }
        return new DiscountSelection(original, appliedPromotions, promotionHits(appliedPromotions, lines),
                plan.coupons, plan.hits, stackingAllowed);
    }

    // ==================== 活动选择 ====================

    /**
     * 平台活动取全单最优一个；商家活动每店铺取最优一个；
     * 平台活动与商家活动的命中行有交集时，保留总优惠更大的一侧。
     */
    private List<Promotion> selectPromotions(List<Promotion> promotions, List<OrderLine> lines, LocalDateTime now) {
        if (promotions == null || promotions.isEmpty()) {
            return List.of();
        }
        Promotion bestPlatform = null;
        Money bestPlatformDiscount = Money.zero();
        Map<Long, Promotion> bestMerchantByShop = new LinkedHashMap<>();
        Map<Long, Money> bestMerchantDiscount = new LinkedHashMap<>();
        for (Promotion promotion : promotions) {
            if (promotion == null || !promotion.isActiveAt(now)) {
                continue;
            }
            Money discount = promotion.discountFor(lines);
            if (!discount.isPositive()) {
                continue;
            }
            if (promotion.isMerchantActivity()) {
                Money current = bestMerchantDiscount.getOrDefault(promotion.getShopId(), Money.zero());
                if (discount.compareTo(current) > 0 || (discount.compareTo(current) == 0
                        && smallerId(promotion, bestMerchantByShop.get(promotion.getShopId())))) {
                    bestMerchantByShop.put(promotion.getShopId(), promotion);
                    bestMerchantDiscount.put(promotion.getShopId(), discount);
                }
            } else if (bestPlatform == null
                    || discount.compareTo(bestPlatformDiscount) > 0
                    || (discount.compareTo(bestPlatformDiscount) == 0 && smallerId(promotion, bestPlatform))) {
                bestPlatform = promotion;
                bestPlatformDiscount = discount;
            }
        }

        List<Promotion> merchants = new ArrayList<>(bestMerchantByShop.values());
        Money merchantTotal = merchants.stream()
                .map(promotion -> promotion.discountFor(lines))
                .reduce(Money.zero(), Money::add);
        if (bestPlatform != null && !merchants.isEmpty() && conflicts(bestPlatform, merchants, lines)) {
            // 冲突：平台活动与商家活动不可同时生效，取总优惠更大者
            return merchantTotal.compareTo(bestPlatformDiscount) > 0 ? merchants : List.of(bestPlatform);
        }
        List<Promotion> applied = new ArrayList<>();
        if (bestPlatform != null) {
            applied.add(bestPlatform);
        }
        applied.addAll(merchants);
        return applied;
    }

    /** 平台活动命中行是否与任一商家活动命中行相交 */
    private boolean conflicts(Promotion platform, List<Promotion> merchants, List<OrderLine> lines) {
        return lines.stream().anyMatch(line ->
                platform.getScope().matches(line, platform.getShopId())
                        && merchants.stream().anyMatch(promotion ->
                        promotion.getScope().matches(line, promotion.getShopId())));
    }

    private boolean smallerId(Promotion candidate, Promotion current) {
        return candidate != null && current != null && candidate.getId() < current.getId();
    }

    private List<Money> promotionHits(List<Promotion> applied, List<OrderLine> lines) {
        return applied.stream().map(promotion -> promotion.discountFor(lines)).toList();
    }

    // ==================== 优惠券选择 ====================

    private record CouponPlan(List<CouponOffer> coupons, List<Money> hits, Money total) {
    }

    private static final CouponPlan EMPTY_PLAN = new CouponPlan(List.of(), List.of(), Money.zero());

    /** 自动选券：按单券优惠降序贪心取前 K 张（受上限与剩余应付约束） */
    private CouponPlan autoCoupons(List<CouponOffer> usableOffers, List<OrderLine> lines,
                                   PromotionSettings settings, Money budget) {
        if (usableOffers.isEmpty()) {
            return EMPTY_PLAN;
        }
        List<CouponOffer> sorted = usableOffers.stream()
                .sorted(Comparator.comparing((CouponOffer offer) -> offer.discountFor(lines)).reversed()
                        .thenComparing(offer -> offer.coupon().getId()))
                .limit(Math.min(settings.maxCouponsPerOrder(), MAX_COUPON_CANDIDATES))
                .toList();
        return assemblePlan(sorted, lines, budget);
    }

    /** 用户指定券：校验可用与张数上限后按同一套截断规则组装 */
    private CouponPlan requestedCoupons(List<Long> selectedCouponIds,
                                        List<CouponOffer> usableOffers,
                                        List<OrderLine> lines,
                                        PromotionSettings settings,
                                        Money budget) {
        Map<Long, CouponOffer> byId = new LinkedHashMap<>();
        usableOffers.forEach(offer -> byId.put(offer.coupon().getId(), offer));
        List<CouponOffer> picked = new ArrayList<>();
        for (Long couponId : selectedCouponIds.stream().distinct().toList()) {
            CouponOffer offer = byId.get(couponId);
            if (offer == null) {
                throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE);
            }
            picked.add(offer);
        }
        if (picked.size() > settings.maxCouponsPerOrder()) {
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE,
                    "单笔订单最多叠加 " + settings.maxCouponsPerOrder() + " 张优惠券");
        }
        for (CouponOffer offer : picked) {
            if (!offer.discountFor(lines).isPositive()) {
                throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE, "优惠券不满足使用条件");
            }
        }
        return assemblePlan(picked, lines, budget);
    }

    /**
     * 把候选券组装成实际生效组合：按优惠降序逐张抵扣，预算（商品总额减已生效活动优惠）
     * 不足时截断当前券并舍弃其余；抵扣为 0 的券不进入结果。输出按券 id 升序，保证记账顺序稳定。
     */
    private CouponPlan assemblePlan(List<CouponOffer> candidates, List<OrderLine> lines, Money budget) {
        Map<Long, Money> hitByCouponId = new LinkedHashMap<>();
        List<CouponOffer> greedy = candidates.stream()
                .sorted(Comparator.comparing((CouponOffer offer) -> offer.discountFor(lines)).reversed())
                .toList();
        for (CouponOffer offer : greedy) {
            if (!budget.isPositive()) {
                break;
            }
            Money hit = offer.discountFor(lines).min(budget);
            if (!hit.isPositive()) {
                break;
            }
            hitByCouponId.put(offer.coupon().getId(), hit);
            budget = budget.subtract(hit);
        }
        List<CouponOffer> coupons = candidates.stream()
                .filter(offer -> hitByCouponId.containsKey(offer.coupon().getId()))
                .sorted(Comparator.comparing(offer -> offer.coupon().getId()))
                .toList();
        List<Money> hits = coupons.stream()
                .map(offer -> hitByCouponId.get(offer.coupon().getId()))
                .toList();
        return new CouponPlan(coupons, hits, hits.stream().reduce(Money.zero(), Money::add));
    }
}
