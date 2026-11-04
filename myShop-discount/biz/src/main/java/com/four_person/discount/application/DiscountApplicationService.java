package com.four_person.discount.application;

import com.four_person.discount.api.dto.AvailableCoupon;
import com.four_person.discount.api.dto.DiscountItem;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.discount.api.dto.ProductPriceVO;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.discount.domain.model.CouponOffer;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.DiscountRule;
import com.four_person.discount.domain.model.DiscountSelection;
import com.four_person.discount.domain.model.LineBreakdown;
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
import com.four_person.discount.enums.RuleType;
import com.four_person.discount.enums.ScopeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
     *
     * @param selectedPromotionIds null = 引擎自动选最优组合；非 null = 用户指定参与的活动（空列表 = 不参与活动）
     */
    @Transactional(readOnly = true)
    public DiscountResultVO calculate(Long userId, List<Long> selectedCouponIds,
                                      List<Long> selectedPromotionIds, List<DiscountItem> items) {
        LocalDateTime now = LocalDateTime.now();
        List<OrderLine> lines = toLines(items);
        List<Promotion> promotions = effectivePromotions(now);
        List<CouponOffer> offers = offers(userId, now);

        DiscountSelection selection = discountPricingService.calculate(
                lines, promotions, offers, selectedCouponIds, selectedPromotionIds, now);
        return toResultVO(selection, lines, promotions, now);
    }

    /**
     * 商品展示「单件最低到手价」：每件商品按「只买 1 件」独立算价，
     * 套用进行中的活动与登录用户满足门槛的券（匿名用户只有活动价），
     * 与结算页共用同一套引擎，活动分侧、券门槛与叠加互斥规则完全一致。
     *
     * <p>商品明细里的 quantity 会被忽略：商品卡展示的是单件价格。</p>
     */
    @Transactional(readOnly = true)
    public List<ProductPriceVO> productPrices(Long userId, List<DiscountItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> promotions = effectivePromotions(now);
        List<CouponOffer> offers = offers(userId, now);
        List<ProductPriceVO> result = new ArrayList<>(items.size());
        for (DiscountItem item : items) {
            List<OrderLine> lines = List.of(new OrderLine(item.getProductId(), item.getCategoryId(),
                    item.getShopId(), Money.of(item.getPrice()), 1));
            DiscountSelection selection = discountPricingService.calculate(
                    lines, promotions, offers, null, null, now);
            LineBreakdown row = discountPricingService.breakDown(lines, selection).get(0);
            ProductPriceVO vo = new ProductPriceVO();
            vo.setProductId(item.getProductId());
            vo.setOriginalPrice(row.originalSubtotal());
            vo.setPromotionDiscount(row.promotionDiscount());
            vo.setCouponDiscount(row.couponDiscount());
            vo.setFinalPrice(row.finalSubtotal());
            vo.setPromotions(row.promotions().stream()
                    .map(hit -> priceHit(hit.id(), hit.title(), hit.amount())).toList());
            vo.setCoupons(row.coupons().stream()
                    .map(hit -> priceHit(hit.id(), hit.title(), hit.amount())).toList());
            result.add(vo);
        }
        return result;
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

    private DiscountResultVO toResultVO(DiscountSelection selection, List<OrderLine> lines,
                                        List<Promotion> applicablePromotions, LocalDateTime now) {
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
        vo.setAppliedPromotionIds(selection.promotions().stream().map(Promotion::getId).toList());
        vo.setPromotionCouponStackable(selection.promotionCouponStackable());
        vo.setPromotionOptions(promotionOptions(applicablePromotions, lines, now,
                vo.getAppliedPromotionIds()));
        vo.setItemDiscounts(itemDiscounts(lines, selection));
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

    /**
     * 结算页可勾选的活动选项：本单可参与（进行中 + 范围命中 + 有优惠）的全部活动，
     * 每项带「本单可减金额」与规则描述；chosen 标记当前算价实际生效的活动。
     */
    private List<DiscountResultVO.PromotionOption> promotionOptions(List<Promotion> promotions,
                                                                    List<OrderLine> lines,
                                                                    LocalDateTime now,
                                                                    List<Long> appliedIds) {
        List<DiscountResultVO.PromotionOption> options = new ArrayList<>();
        for (Promotion promotion : promotions) {
            if (promotion == null || !promotion.isActiveAt(now)) {
                continue;
            }
            Money discount = promotion.discountFor(lines);
            if (!discount.isPositive()) {
                continue;
            }
            DiscountResultVO.PromotionOption option = new DiscountResultVO.PromotionOption();
            option.setPromotionId(promotion.getId());
            option.setTitle(promotion.getTitle());
            option.setShopId(promotion.getShopId());
            option.setShopName(promotion.isMerchantActivity() ? shopName(promotion.getShopId()) : null);
            option.setScopeText(scopeText(promotion.getScope().type()));
            option.setRuleText(ruleTextOf(promotion));
            option.setDiscount(discount.amount());
            option.setCouponStackable(promotion.isCouponStackable());
            option.setChosen(appliedIds != null && appliedIds.contains(promotion.getId()));
            options.add(option);
        }
        // 平台活动在前，商家活动按店铺号排序，展示顺序稳定
        options.sort(Comparator.comparing((DiscountResultVO.PromotionOption option) ->
                        option.getShopId() == null || option.getShopId() == 0 ? Long.MIN_VALUE : option.getShopId())
                .thenComparing(DiscountResultVO.PromotionOption::getPromotionId));
        return options;
    }

    /** 每行商品的优惠拆解（原价小计 / 参与了哪些活动与券 / 优惠后小计） */
    private List<DiscountResultVO.ItemDiscount> itemDiscounts(List<OrderLine> lines, DiscountSelection selection) {
        return discountPricingService.breakDown(lines, selection).stream()
                .map(row -> {
                    DiscountResultVO.ItemDiscount vo = new DiscountResultVO.ItemDiscount();
                    vo.setProductId(row.productId());
                    vo.setOriginalSubtotal(row.originalSubtotal());
                    vo.setPromotionDiscount(row.promotionDiscount());
                    vo.setCouponDiscount(row.couponDiscount());
                    vo.setFinalSubtotal(row.finalSubtotal());
                    vo.setPromotions(row.promotions().stream()
                            .map(hit -> appliedHit(hit.id(), hit.title(), hit.amount())).toList());
                    vo.setCoupons(row.coupons().stream()
                            .map(hit -> appliedHit(hit.id(), hit.title(), hit.amount())).toList());
                    return vo;
                }).toList();
    }

    private DiscountResultVO.AppliedHit appliedHit(Long id, String title, BigDecimal amount) {
        DiscountResultVO.AppliedHit hit = new DiscountResultVO.AppliedHit();
        hit.setId(id);
        hit.setTitle(title);
        hit.setAmount(amount);
        return hit;
    }

    private ProductPriceVO.Hit priceHit(Long id, String title, BigDecimal amount) {
        ProductPriceVO.Hit hit = new ProductPriceVO.Hit();
        hit.setId(id);
        hit.setTitle(title);
        hit.setAmount(amount);
        return hit;
    }

    /** 活动规则的展示文案，如「每满 200 减 30」「9 折，最多减 50」 */
    private String ruleTextOf(Promotion promotion) {
        DiscountRule rule = promotion.getRule();
        if (rule.type() == RuleType.FULL_REDUCTION) {
            String amount = rule.discountAmount().amount().stripTrailingZeros().toPlainString();
            if (rule.perEvery() > 0) {
                return "每满 " + rule.perEvery() + " 减 " + amount;
            }
            return "满 " + rule.thresholdAmount().amount().stripTrailingZeros().toPlainString()
                    + " 减 " + amount;
        }
        String text = rule.discountRate().multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString() + " 折";
        if (rule.maxDiscount() != null && rule.maxDiscount().isPositive()) {
            text += "，最多减 " + rule.maxDiscount().amount().stripTrailingZeros().toPlainString();
        }
        return text;
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
