package com.four_person.system.infrastructure.support;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 读取平台运营配置（platform 服务的 {@code config_item} 表）。
 * <p>
 * 各服务共用同一数据库，系统服务只需少量积分规则，
 * 因此按 key 直读配置表，避免为一次读取引入平台服务的运行时依赖。
 * 不做缓存：配置中心保存后立即对积分计算生效。
 */
@Component
@RequiredArgsConstructor
public class PlatformConfigReader {

    private final JdbcTemplate jdbcTemplate;

    public String get(String group, String key, String fallback) {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM config_item WHERE config_group = ? AND config_key = ? LIMIT 1",
                    String.class, group, key);
            return values.isEmpty() || !StringUtils.hasText(values.get(0)) ? fallback : values.get(0);
        } catch (DataAccessException ex) {
            return fallback;
        }
    }

    public BigDecimal getDecimal(String group, String key, BigDecimal fallback) {
        String raw = get(group, key, null);
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
