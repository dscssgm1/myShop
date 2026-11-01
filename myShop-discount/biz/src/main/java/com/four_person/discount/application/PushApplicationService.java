package com.four_person.discount.application;

import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.api.dto.PushResultVO;
import com.four_person.discount.application.port.OrderQueryPort;
import com.four_person.discount.domain.model.UserPurchaseStats;
import com.four_person.discount.domain.service.UserSegmentationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushApplicationService {

    private static final int MAX_ITEMS = 5;

    private final OrderQueryPort orderQueryPort;
    private final UserSegmentationService segmentationService;
    private final PromotionApplicationService promotionApplicationService;
    private final CouponApplicationService couponApplicationService;

    public PushResultVO push(Long userId) {
        UserSegmentationService.Segment segment;
        try {
            UserPurchaseStats stats = orderQueryPort.stats(userId);
            segment = segmentationService.segment(stats);
        } catch (Exception ex) {
            log.warn("获取用户消费统计失败，使用通用推送: {}", ex.getMessage());
            segment = segmentationService.unknown();
        }

        List<PromotionVO> promotions = selectPromotions(
                promotionApplicationService.list("active"), segment.segment());
        List<CouponTemplateVO> coupons = selectCoupons(
                couponApplicationService.listTemplates(userId).stream()
                        .filter(coupon -> !Boolean.TRUE.equals(coupon.getClaimed()))
                        .toList(),
                segment.segment(), segment.valueTier());

        PushResultVO result = new PushResultVO();
        result.setUserId(userId);
        result.setSegment(segment.segment());
        result.setSegmentText(segment.segmentText());
        result.setValueTier(segment.valueTier());
        result.setReasons(segment.reasons());
        result.setPromotions(promotions);
        result.setCoupons(coupons);
        return result;
    }

    private List<PromotionVO> selectPromotions(List<PromotionVO> promotions, String segment) {
        List<PromotionVO> filtered = promotions;
        if ("NEW".equals(segment)) {
            List<PromotionVO> newcomer = promotions.stream()
                    .filter(promotion -> contains(promotion.getType(), "领券")
                            || contains(promotion.getTitle(), "新人"))
                    .toList();
            if (!newcomer.isEmpty()) {
                filtered = newcomer;
            }
        } else if ("AT_RISK".equals(segment) || "LOST".equals(segment)) {
            List<PromotionVO> recall = promotions.stream()
                    .filter(promotion -> contains(promotion.getType(), "平台大促")
                            || contains(promotion.getType(), "品类促销"))
                    .toList();
            if (!recall.isEmpty()) {
                filtered = recall;
            }
        }
        return filtered.stream().limit(MAX_ITEMS).toList();
    }

    private List<CouponTemplateVO> selectCoupons(List<CouponTemplateVO> coupons,
                                                 String segment,
                                                 String valueTier) {
        List<CouponTemplateVO> filtered;
        if ("NEW".equals(segment) || "LOW".equals(valueTier)) {
            filtered = filterByThreshold(coupons, new BigDecimal("300"), true);
        } else if ("HIGH".equals(valueTier)) {
            filtered = filterByThreshold(coupons, new BigDecimal("1000"), false);
        } else if ("AT_RISK".equals(segment) || "LOST".equals(segment) || "UNKNOWN".equals(segment)) {
            filtered = coupons;
        } else {
            filtered = filterByThreshold(coupons, new BigDecimal("1000"), true);
        }
        if (filtered.isEmpty()) {
            filtered = coupons;
        }
        return filtered.stream()
                .sorted(Comparator.comparing(coupon -> coupon.getThresholdAmount() == null
                        ? BigDecimal.ZERO : coupon.getThresholdAmount()))
                .limit(MAX_ITEMS)
                .toList();
    }

    private List<CouponTemplateVO> filterByThreshold(List<CouponTemplateVO> coupons,
                                                     BigDecimal threshold,
                                                     boolean upperBound) {
        return coupons.stream()
                .filter(coupon -> {
                    BigDecimal value = coupon.getThresholdAmount() == null
                            ? BigDecimal.ZERO : coupon.getThresholdAmount();
                    return upperBound ? value.compareTo(threshold) <= 0 : value.compareTo(threshold) >= 0;
                })
                .toList();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }
}
