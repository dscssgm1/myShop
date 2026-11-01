package com.four_person.discount.domain.service;

import com.four_person.discount.domain.model.UserPurchaseStats;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 简化版 RFM 用户分层领域服务。
 */
@Service
public class UserSegmentationService {

    private static final BigDecimal HIGH_VALUE = new BigDecimal("5000");
    private static final BigDecimal MID_VALUE = new BigDecimal("1000");

    public Segment segment(UserPurchaseStats stats) {
        if (stats == null || stats.orderCount() <= 0) {
            return new Segment("NEW", "新用户", "LOW",
                    List.of("新用户专享礼包", "首单立减"));
        }
        BigDecimal spent = stats.totalSpent() == null ? BigDecimal.ZERO : stats.totalSpent();
        String valueTier = spent.compareTo(HIGH_VALUE) >= 0 ? "HIGH"
                : spent.compareTo(MID_VALUE) >= 0 ? "MID" : "LOW";

        LocalDateTime lastOrderAt = stats.lastOrderAt();
        long days = lastOrderAt == null
                ? Long.MAX_VALUE
                : ChronoUnit.DAYS.between(lastOrderAt.toLocalDate(), LocalDate.now());

        if (days <= 30) {
            return new Segment("ACTIVE", "活跃用户", valueTier,
                    List.of("HIGH".equals(valueTier) ? "高价值活跃用户，优先推荐平台大促" : "近期有购买，推荐当前活动"));
        }
        if (days <= 90) {
            return new Segment("AT_RISK", "流失预警", valueTier,
                    List.of("超过 30 天未下单，推送召回优惠"));
        }
        return new Segment("LOST", "沉睡用户", valueTier,
                List.of("长期未下单，推送大额召回券"));
    }

    public Segment unknown() {
        return new Segment("UNKNOWN", "未知用户", "UNKNOWN",
                List.of("暂时无法获取消费数据，展示当前热门活动"));
    }

    public record Segment(String segment, String segmentText, String valueTier, List<String> reasons) {
    }
}
