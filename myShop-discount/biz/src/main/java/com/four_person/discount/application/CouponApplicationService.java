package com.four_person.discount.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;
import com.four_person.discount.api.dto.CouponTemplateRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.discount.api.dto.DiscountItem;
import com.four_person.discount.api.dto.UserCouponVO;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.discount.domain.event.CouponClaimedEvent;
import com.four_person.discount.domain.event.CouponReleasedEvent;
import com.four_person.discount.domain.event.CouponUsedEvent;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.OrderLine;
import com.four_person.discount.domain.model.Scope;
import com.four_person.discount.domain.model.UserCoupon;
import com.four_person.discount.domain.repository.CouponTemplateRepository;
import com.four_person.discount.domain.repository.UserCouponRepository;
import com.four_person.discount.enums.CouponStatus;
import com.four_person.discount.enums.DiscountErrorCode;
import com.four_person.discount.enums.ScopeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class CouponApplicationService {

    private final CouponTemplateRepository templateRepository;
    private final UserCouponRepository userCouponRepository;
    private final ShopQueryPort shopQueryPort;
    private final ApplicationEventPublisher eventPublisher;

    /** 领券中心：平台券 + 商家券可免费领取（积分兑换券只在积分商城） */
    public List<CouponTemplateVO> listTemplates(Long userId) {        LocalDateTime now = LocalDateTime.now();
        List<CouponTemplate> templates = templateRepository.findActive(now).stream()
                .filter(template -> !template.isPointsExclusive())
                .toList();
        Map<Long, Long> claimedCounts = userId == null
                ? Collections.emptyMap()
                : userCouponRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserCoupon::getTemplateId, Collectors.counting()));
        return templates.stream()
                .map(template -> toTemplateVO(template, claimedCounts.getOrDefault(template.getId(), 0L) > 0))
                .toList();
    }

    /**
     * 结算页「本单可领」：按当前商品明细过滤可领券——范围内小计达门槛且抵扣为正才显示，
     * 每张带本单可抵扣金额；已领完/达个人限领的模板不再列出。
     */
    @Transactional(readOnly = true)
    public List<CouponTemplateVO> claimableForOrder(Long userId, List<DiscountItem> items) {
        List<OrderLine> lines = items.stream()
                .map(item -> new OrderLine(item.getProductId(), item.getCategoryId(), item.getShopId(),
                        Money.of(item.getPrice()), item.getQuantity()))
                .toList();
        return claimableForLines(userId, lines);
    }

    /** 按已构建的商品行过滤「本单可领」券模板 */
    private List<CouponTemplateVO> claimableForLines(Long userId, List<OrderLine> lines) {
        LocalDateTime now = LocalDateTime.now();
        List<CouponTemplate> templates = templateRepository.findActive(now).stream()
                .filter(template -> !template.isPointsExclusive())
                .filter(template -> template.getRemaining() > 0)
                .filter(template -> template.discountFor(lines).amount().signum() > 0)
                .toList();
        if (templates.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Long> claimedCounts = userId == null
                ? Collections.emptyMap()
                : userCouponRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserCoupon::getTemplateId, Collectors.counting()));
        List<CouponTemplateVO> result = new ArrayList<>();
        for (CouponTemplate template : templates) {
            long claimedByUser = claimedCounts.getOrDefault(template.getId(), 0L);
            if (userId != null && claimedByUser >= template.getPerUserLimit()) {
                continue; // 已达个人限领上限，不再列出
            }
            CouponTemplateVO vo = toTemplateVO(template, claimedByUser > 0);
            vo.setApplicableAmount(template.discountFor(lines).amount());
            result.add(vo);
        }
        return result;
    }

    /** 积分商城货架：设了积分价的券模板；claimed 标记当前用户是否已达限领上限 */
    public List<CouponTemplateVO> pointsMallItems(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<CouponTemplate> templates = templateRepository.findPointsExchangeable(now);
        Map<Long, Long> claimedCounts = userId == null
                ? Collections.emptyMap()
                : userCouponRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserCoupon::getTemplateId, Collectors.counting()));
        return templates.stream()
                .map(template -> {
                    long claimedByUser = claimedCounts.getOrDefault(template.getId(), 0L);
                    CouponTemplateVO vo = toTemplateVO(template, claimedByUser >= template.getPerUserLimit());
                    return vo;
                })
                .toList();
    }

    /** 商家侧自己发放的券（含已结束） */
    public List<CouponTemplateVO> listByShop(Long shopId) {
        return templateRepository.findByShop(shopId).stream()
                .map(template -> toTemplateVO(template, false))
                .toList();
    }

    /**
     * 发放优惠券模板。ownerShopId 为 null 表示平台券（全场/指定分类）；
     * 商家券范围限本店商品或指定分类，费用由商家承担。
     */
    public CouponTemplateVO createTemplate(Long ownerShopId, CouponTemplateRequest request) {
        long owner = ownerShopId == null ? 0 : ownerShopId;
        ScopeType scopeType = parseScopeType(request.getScopeType(), owner);
        Set<Long> scopeIds = request.getScopeIds() == null ? Set.of() : Set.copyOf(request.getScopeIds());
        CouponTemplate template = new CouponTemplate(null, owner, request.getTitle(),
                Money.of(request.getThresholdAmount()), Money.of(request.getDiscountAmount()),
                new Scope(scopeType, scopeIds), request.getTotalStock(), 0, request.getPerUserLimit(),
                request.getPointsPrice(),
                request.getCouponStackable(), request.getStackable(),
                request.getValidStart(), request.getValidEnd(), request.getStatus());
        return toTemplateVO(templateRepository.save(template), false);
    }

    public void endTemplate(Long id) {
        CouponTemplate existing = load(id);
        if (existing.isMerchantCoupon()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "商家券请在商家端维护");
        }
        templateRepository.save(existing.ended());
    }

    public void endTemplateForShop(Long shopId, Long id) {
        CouponTemplate existing = load(id);
        if (!existing.isMerchantCoupon() || !existing.getShopId().equals(shopId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权操作其他店铺的优惠券");
        }
        templateRepository.save(existing.ended());
    }

    private CouponTemplate load(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.COUPON_NOT_FOUND));
    }

    private ScopeType parseScopeType(String raw, long owner) {
        ScopeType scopeType;
        try {
            scopeType = StringUtils.hasText(raw)
                    ? ScopeType.valueOf(raw.trim().toUpperCase()) : ScopeType.ALL;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "不支持的适用范围");
        }
        if (scopeType == ScopeType.PRODUCT) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "已不支持指定商品范围，请选择全场或指定分类");
        }
        if (owner > 0) {
            // 商家券只能圈本店：本店全部商品(SHOP) 或 本店指定分类(CATEGORY)
            return scopeType == ScopeType.CATEGORY ? ScopeType.CATEGORY : ScopeType.SHOP;
        }
        if (scopeType == ScopeType.SHOP) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "平台券不能使用店铺范围");
        }
        return scopeType == ScopeType.CATEGORY ? ScopeType.CATEGORY : ScopeType.ALL;
    }

    /** 免费领券中心领取：积分兑换券不允许在此领取 */
    @Transactional(rollbackFor = Exception.class)
    public UserCouponVO claim(Long userId, Long templateId) {
        CouponTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.COUPON_NOT_FOUND));
        if (template.isPointsExclusive()) {
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE,
                    "该券为积分兑换券，请到积分商城用积分兑换");
        }
        return doClaim(userId, template);
    }

    /** 积分商城兑换领取（system 服务扣积分成功后调用）：允许积分兑换券 */
    @Transactional(rollbackFor = Exception.class)
    public UserCouponVO claimForUser(Long userId, Long templateId) {
        CouponTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.COUPON_NOT_FOUND));
        return doClaim(userId, template);
    }

    private UserCouponVO doClaim(Long userId, CouponTemplate template) {
        LocalDateTime now = LocalDateTime.now();
        int claimedByUser = userCouponRepository.countByUserAndTemplate(userId, template.getId());
        template.ensureClaimable(claimedByUser, now);
        if (!templateRepository.increaseClaimed(template.getId())) {
            throw new BusinessException(DiscountErrorCode.COUPON_STOCK_EMPTY);
        }
        UserCoupon coupon = userCouponRepository.save(
                UserCoupon.claim(userId, template.getId(), now, template.getValidEnd()));
        eventPublisher.publishEvent(new CouponClaimedEvent(userId, coupon.getId(), template.getId()));
        return toUserCouponVO(coupon, template);
    }

    public List<UserCouponVO> myCoupons(Long userId, String status) {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> coupons;
        if (StringUtils.hasText(status)) {
            try {
                coupons = userCouponRepository.findByUserIdAndStatus(userId, CouponStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                coupons = userCouponRepository.findByUserId(userId);
            }
        } else {
            coupons = userCouponRepository.findByUserId(userId);
        }
        if (coupons.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CouponTemplate> templates = templateRepository.findByIds(
                        coupons.stream().map(UserCoupon::getTemplateId).distinct().toList())
                .stream().collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));
        return coupons.stream().map(coupon -> {
            UserCoupon effective = coupon;
            if (coupon.getStatus() == CouponStatus.UNUSED && coupon.isExpired(now)) {
                userCouponRepository.markExpired(coupon.getId());
                effective = coupon.expire();
            }
            return toUserCouponVO(effective, templates.get(effective.getTemplateId()));
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void useCoupon(Long userId, Long couponId, String orderNo) {
        UserCoupon coupon = userCouponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.COUPON_NOT_FOUND));
        if (!coupon.getUserId().equals(userId)) {
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_FOUND);
        }
        if (coupon.getStatus() == CouponStatus.USED && orderNo.equals(coupon.getOrderNo())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        coupon.use(orderNo, now);
        if (!userCouponRepository.markUsed(couponId, userId, orderNo, now)) {
            UserCoupon latest = userCouponRepository.findById(couponId).orElse(null);
            if (latest != null && latest.getStatus() == CouponStatus.USED && orderNo.equals(latest.getOrderNo())) {
                return;
            }
            throw new BusinessException(DiscountErrorCode.COUPON_NOT_AVAILABLE);
        }
        eventPublisher.publishEvent(new CouponUsedEvent(userId, couponId, orderNo));
    }

    public void releaseCoupon(Long couponId, String orderNo) {
        if (userCouponRepository.release(couponId, orderNo)) {
            eventPublisher.publishEvent(new CouponReleasedEvent(couponId, orderNo));
        }
    }

    private CouponTemplateVO toTemplateVO(CouponTemplate template, boolean claimed) {
        CouponTemplateVO vo = new CouponTemplateVO();
        vo.setId(template.getId());
        vo.setShopId(template.getShopId());
        vo.setShopName(template.isMerchantCoupon() ? shopQueryPort.shopName(template.getShopId()) : null);
        vo.setTitle(template.getTitle());
        vo.setThresholdAmount(template.getThresholdAmount().amount());
        vo.setDiscountAmount(template.getDiscountAmount().amount());
        vo.setScopeType(template.getScope().type().name());
        vo.setScopeText(scopeText(template.getScope().type()));
        vo.setScopeIds(template.getScope().ids().stream().sorted().toList());
        vo.setTotalStock(template.getTotalStock());
        vo.setClaimedCount(template.getClaimedCount());
        vo.setRemaining(template.getRemaining());
        vo.setPerUserLimit(template.getPerUserLimit());
        vo.setPointsPrice(template.getPointsPrice());
        vo.setCouponStackable(template.isCouponStackable());
        vo.setStackable(template.isStackable());
        vo.setValidStart(template.getValidStart());
        vo.setValidEnd(template.getValidEnd());
        vo.setStatus(template.getStatus());
        vo.setClaimed(claimed);
        return vo;
    }

    private UserCouponVO toUserCouponVO(UserCoupon coupon, CouponTemplate template) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(coupon.getId());
        vo.setTemplateId(coupon.getTemplateId());
        vo.setStatus(coupon.getStatus().name());
        vo.setStatusText(coupon.getStatus().getText());
        vo.setOrderNo(coupon.getOrderNo());
        vo.setClaimedAt(coupon.getClaimedAt());
        vo.setUsedAt(coupon.getUsedAt());
        vo.setExpiredAt(coupon.getExpiredAt());
        if (template != null) {
            vo.setTitle(template.getTitle());
            vo.setThresholdAmount(template.getThresholdAmount().amount());
            vo.setDiscountAmount(template.getDiscountAmount().amount());
            vo.setScopeType(template.getScope().type().name());
            vo.setScopeText(scopeText(template.getScope().type()));
            vo.setShopId(template.getShopId());
            vo.setShopName(template.isMerchantCoupon() ? shopQueryPort.shopName(template.getShopId()) : null);
        }
        return vo;
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
