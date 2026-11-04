package com.four_person.order.infrastructure.support;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 读取积分规则配置（platform 服务的 config_item 表）。
 * <p>
 * 各服务共用同一数据库，订单服务只需要下单抵现的两个比例项，
 * 按 key 直读配置表，配置中心保存后立即对结算生效。
 */
@Component
@RequiredArgsConstructor
public class PointsRuleReader {

    private static final String POINTS_GROUP = "points-rule";
    /** 多少积分抵 1 元 */
    private static final String KEY_DEDUCT_POINTS_PER_YUAN = "deduct-points-per-yuan";
    /** 最多抵应付金额的百分比 */
    private static final String KEY_DEDUCT_MAX_PERCENT = "deduct-max-percent";

    private final JdbcTemplate jdbcTemplate;

    /** 抵现比例：多少积分抵 1 元；<=0 或未配置表示抵现未开启 */
    public int pointsPerYuan() {
        BigDecimal value = getDecimal(KEY_DEDUCT_POINTS_PER_YUAN, BigDecimal.ZERO);
        return value.signum() <= 0 ? 0 : value.setScale(0, java.math.RoundingMode.DOWN).intValue();
    }

    /** 抵现上限：最多抵应付金额的百分比；<=0 表示抵现未开启 */
    public int maxDeductPercent() {
        BigDecimal value = getDecimal(KEY_DEDUCT_MAX_PERCENT, BigDecimal.ZERO);
        return value.signum() <= 0 ? 0 : value.setScale(0, java.math.RoundingMode.DOWN).intValue();
    }

    private BigDecimal getDecimal(String key, BigDecimal fallback) {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM config_item WHERE config_group = ? AND config_key = ? LIMIT 1",
                    String.class, POINTS_GROUP, key);
            if (values.isEmpty() || !StringUtils.hasText(values.get(0))) {
                return fallback;
            }
            return new BigDecimal(values.get(0).trim());
        } catch (Exception ex) {
            // 配置表缺失/未初始化等任何异常都不阻断下单，视为抵现未开启
            return fallback;
        }
    }
}
