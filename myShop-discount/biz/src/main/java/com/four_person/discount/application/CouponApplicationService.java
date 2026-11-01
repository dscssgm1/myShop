package com.four_person.discount.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.dto.CouponTemplateRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.discount.api.dto.UserCouponVO;
import com.four_person.discount.domain.event.CouponClaimedEvent;
import com.four_person.discount.domain.event.CouponReleasedEvent;
import com.four_person.discount.domain.event.CouponUsedEvent;
import com.four_person.discount.domain.model.CouponTemplate;
import com.four_person.discount.domain.model.Money;
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
    private final ApplicationEventPublisher eventPublisher;

    public List<CouponTemplateVO> listTemplates(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<CouponTemplate> templates = templateRepository.findActive(now);
        Map<Long, Long> claimedCounts = userId == null
                ? Collections.emptyMap()
                : userCouponRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserCoupon::getTemplateId, Collectors.counting()));
        return templates.stream()
                .map(template -> toTemplateVO(template, claimedCounts.getOrDefault(template.getId(), 0L) > 0))
                .toList();
    }

    public CouponTemplateVO createTemplate(CouponTemplateRequest request) {
        ScopeType scopeType;
        try {
            scopeType = StringUtils.hasText(request.getScopeType())
                    ? ScopeType.valueOf(request.getScopeType().trim().toUpperCase()) : ScopeType.ALL;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(DiscountErrorCode.INVALID_RULE, "不支持的适用范围");
        }
        Set<Long> scopeIds = request.getScopeIds() == null ? Set.of() : Set.copyOf(request.getScopeIds());
        CouponTemplate template = new CouponTemplate(null, request.getTitle(),
                Money.of(request.getThresholdAmount()), Money.of(request.getDiscountAmount()),
                new Scope(scopeType, scopeIds), request.getTotalStock(), 0, request.getPerUserLimit(),
                request.getValidStart(), request.getValidEnd(), request.getStatus());
        return toTemplateVO(templateRepository.save(template), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserCouponVO claim(Long userId, Long templateId) {
        CouponTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(DiscountErrorCode.COUPON_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        int claimedByUser = userCouponRepository.countByUserAndTemplate(userId, templateId);
        template.ensureClaimable(claimedByUser, now);
        if (!templateRepository.increaseClaimed(templateId)) {
            throw new BusinessException(DiscountErrorCode.COUPON_STOCK_EMPTY);
        }
        UserCoupon coupon = userCouponRepository.save(
                UserCoupon.claim(userId, templateId, now, template.getValidEnd()));
        eventPublisher.publishEvent(new CouponClaimedEvent(userId, coupon.getId(), templateId));
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
        }
        return vo;
    }

    private String scopeText(ScopeType type) {
        return switch (type) {
            case CATEGORY -> "指定分类";
            case PRODUCT -> "指定商品";
            default -> "全品类";
        };
    }
}
