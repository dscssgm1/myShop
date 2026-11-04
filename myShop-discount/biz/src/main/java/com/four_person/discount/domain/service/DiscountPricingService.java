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
 *   <li>优惠券：一单最多叠加 {@link #MAX_COUPONS_PER_ORDER} 张；每张券是否可与其他券叠加
 *       （stackable）、可否与活动同用（couponStackable）由券模板自身配置决定。
 *       每张券的可抵扣额相互独立（min(面额, 范围内小计)），只受「活动优惠后的剩余应付」约束，
 *       因此按单券优惠降序贪心取前 K 张即为最优组合。</li>
 *   <li>叠加：活动与券同用要求两侧都放行——活动侧每个生效活动允许叠券，
 *       券侧所选券均允许与活动同用。自动选券时冲突则静默在「仅活动」「仅券」间取
 *       总优惠更大者；用户指定选券时冲突直接报错。不可叠加的券一单只能单独使用。</li>
 *   <li>封顶：活动 + 券的优惠总额永不超过商品总额（余量不足的券被截断或舍弃）。</li>
 * </ol>
 */
@Service
public class DiscountPricingService {

    /** 一单最多叠加的优惠券张数 */
    public static final int MAX_COUPONS_PER_ORDER = 3;

    /** 自动选券时参与贪心的候选上限，防止极端用户券量拖慢算价 */
    private static final int MAX_COUPON_CANDIDATES = 12;

    public DiscountSelection calculate(List<OrderLine> lines,
                                       List<Promotion> promotions,
                                       List<CouponOffer> offers,
                                       List<Long> selectedCouponIds,
                                       LocalDateTime now) {
        Money original = lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero(), Money::add);
        if (!original.isPositive()) {
            return new DiscountSelection(original, List.of(), List.of(), List.of(), List.of(), false);
        }

        List<Promotion> appliedPromotions = selectPromotions(promotions, lines, now);
        Money promotionDiscount = appliedPromotions.stream()
                .map(promotion -> promotion.discountFor(lines))
                .reduce(Money.zero(), Money::add);
        boolean promotionSideAllows = appliedPromotions.isEmpty()
                || appliedPromotions.stream().allMatch(Promotion::isCouponStackable);

        List<CouponOffer> usableOffers = offers.stream()
                .filter(offer -> offer != null && offer.isUsable(now) && offer.discountFor(lines).isPositive())
                .toList();
        return selectedCouponIds == null || selectedCouponIds.isEmpty()
                ? autoSelect(appliedPromotions, promotionDiscount, promotionSideAllows, usableOffers, lines, original)
                : requestedSelect(original, appliedPromotions, promotionDiscount, promotionSideAllows,
                        usableOffers, lines, selectedCouponIds);
    }

    // ==================== 用户指定选券：冲突直接报错 ====================

    private DiscountSelection requestedSelect(Money original,
                                              List<Promotion> appliedPromotions,
                                              Money promotionDiscount,
                                              boolean promotionSideAllows,
                                              List<CouponOffer> usableOffers,
                                              List<OrderLine> lines,
                                              List<Long> selectedCouponIds) {
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
        if (picked.size() > MAX_COUPONS_PER_ORDER) {
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE,
                    "单笔订单最多叠加 " + MAX_COUPONS_PER_ORDER + " 张优惠券");
        }
        for (CouponOffer offer : picked) {
            if (!offer.discountFor(lines).isPositive()) {
                throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE, "优惠券不满足使用条件");
            }
        }
        if (picked.size() > 1) {
            for (CouponOffer offer : picked) {
                if (!offer.isStackable()) {
                    throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE,
                            "优惠券「" + offer.template().getTitle() + "」不可与其他优惠券叠加使用");
                }
            }
        }
        if (promotionDiscount.isPositive()) {
            if (!promotionSideAllows) {
                throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE,
                        "当前促销活动不可与优惠券叠加使用");
            }
            for (CouponOffer offer : picked) {
                if (!offer.isActivityStackable()) {
                    throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE,
                            "优惠券「" + offer.template().getTitle() + "」不可与促销活动叠加使用");
                }
            }
        }
        // 校验通过：活动 + 券全量叠加，券预算 = 商品总额 - 已生效活动优惠
        CouponPlan plan = assemblePlan(picked, lines, original.subtract(promotionDiscount));
        return new DiscountSelection(original, appliedPromotions, promotionHits(appliedPromotions, lines),
                plan.coupons(), plan.hits(), true);
    }

    // ==================== 自动选券：冲突静默取优 ====================

    private DiscountSelection autoSelect(List<Promotion> appliedPromotions,
                                         Money promotionDiscount,
                                         boolean promotionSideAllows,
                                         List<CouponOffer> usableOffers,
                                         List<OrderLine> lines,
                                         Money original) {
        // 候选一：活动 + 可与活动同用的券（可叠加的贪心多张，不可叠加的单张，取更大者）
        CouponPlan withPromotion = EMPTY_PLAN;
        if (promotionDiscount.isPositive() && promotionSideAllows) {
            List<CouponOffer> activityOk = usableOffers.stream()
                    .filter(CouponOffer::isActivityStackable)
                    .toList();
            withPromotion = bestPlan(activityOk, lines, original.subtract(promotionDiscount));
        }
        Money totalWithPromotion = promotionDiscount.add(withPromotion.total());

        // 候选二：不用活动，仅用券（可叠加的贪心多张，不可叠加的单张，取更大者）
        CouponPlan couponsOnly = bestPlan(usableOffers, lines, original);
        Money totalCouponsOnly = couponsOnly.total();

        if (totalCouponsOnly.compareTo(totalWithPromotion) > 0) {
            // 仅券更优：放弃活动（冲突静默取舍，promotionCouponStackable=false 提示未叠加）
            return new DiscountSelection(original, List.of(), List.of(),
                    couponsOnly.coupons(), couponsOnly.hits(), appliedPromotions.isEmpty());
        }
        return new DiscountSelection(original, appliedPromotions, promotionHits(appliedPromotions, lines),
                withPromotion.coupons(), withPromotion.hits(), promotionSideAllows);
    }

    /**
     * 在给定预算内组合出一组券：可叠加的券贪心多张（受张数上限约束），
     * 不可叠加的券只能单张使用，取总额更大的方案。
     */
    private CouponPlan bestPlan(List<CouponOffer> usableOffers, List<OrderLine> lines, Money budget) {
        List<CouponOffer> stackGroup = usableOffers.stream().filter(CouponOffer::isStackable).toList();
        CouponPlan multi = stackGroup.isEmpty() ? EMPTY_PLAN
                : assemblePlan(candidates(stackGroup, lines), lines, budget);
        CouponPlan solo = bestSolo(usableOffers.stream().filter(offer -> !offer.isStackable()).toList(),
                lines, budget);
        return multi.total().compareTo(solo.total()) >= 0 ? multi : solo;
    }

    /** 自动选券候选：按单券优惠降序（并列取 id 小者）截取前 K 张 */
    private List<CouponOffer> candidates(List<CouponOffer> offers, List<OrderLine> lines) {
        return offers.stream()
                .sorted(Comparator.comparing((CouponOffer offer) -> offer.discountFor(lines)).reversed()
                        .thenComparing(offer -> offer.coupon().getId()))
                .limit(Math.min(MAX_COUPONS_PER_ORDER, MAX_COUPON_CANDIDATES))
                .toList();
    }

    /** 不可叠加的券只能单张使用：取优惠最大的一张（并列取 id 小者），预算不足时被截断 */
    private CouponPlan bestSolo(List<CouponOffer> offers, List<OrderLine> lines, Money budget) {
        if (offers.isEmpty()) {
            return EMPTY_PLAN;
        }
        CouponOffer best = offers.stream()
                .sorted(Comparator.comparing((CouponOffer offer) -> offer.discountFor(lines)).reversed()
                        .thenComparing(offer -> offer.coupon().getId()))
                .findFirst()
                .orElseThrow();
        return assemblePlan(List.of(best), lines, budget);
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

    // ==================== 优惠券组装 ====================

    private record CouponPlan(List<CouponOffer> coupons, List<Money> hits, Money total) {
    }

    private static final CouponPlan EMPTY_PLAN = new CouponPlan(List.of(), List.of(), Money.zero());

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
