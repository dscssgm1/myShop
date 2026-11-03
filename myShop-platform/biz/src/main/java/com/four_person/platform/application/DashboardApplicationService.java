package com.four_person.platform.application;

import com.four_person.common.web.AccessGuard;
import com.four_person.platform.api.dto.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台数据看板：本项目多服务共用一个数据库，直接用 JdbcTemplate 做聚合查询。
 */
@Service
@RequiredArgsConstructor
public class DashboardApplicationService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardVO overview() {
        AccessGuard.requireGroup("PLATFORM");
        DashboardVO vo = new DashboardVO();

        vo.setTodayOrderCount(count("""
                SELECT COUNT(*) FROM order_info
                 WHERE deleted = 0 AND DATE(created_at) = CURDATE()"""));
        vo.setTodaySales(decimal("""
                SELECT IFNULL(SUM(pay_amount), 0) FROM order_info
                 WHERE deleted = 0 AND status IN ('PAID','SHIPPED','COMPLETED')
                   AND DATE(created_at) = CURDATE()"""));
        vo.setTotalUsers(count("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        vo.setTotalProducts(count("SELECT COUNT(*) FROM product WHERE deleted = 0 AND status = 1"));
        vo.setTotalShops(count("SELECT COUNT(*) FROM shop WHERE deleted = 0 AND status = 1"));
        // 待审核 = 审核中心五类入口之和，各项口径与归属服务的统计一致（与前端角标同源）
        vo.setPendingAudits(
                count("SELECT COUNT(*) FROM product WHERE deleted = 0 AND (audit_status = 'PENDING' OR pending_data IS NOT NULL)")
                        + count("SELECT COUNT(*) FROM shop_apply WHERE deleted = 0 AND status = 'PENDING'")
                        + count("SELECT COUNT(*) FROM audit_record WHERE deleted = 0 AND status IN ('PENDING','FIRST_APPROVED')")
                        + count("SELECT COUNT(*) FROM review WHERE deleted = 0 AND (status = 'PENDING' OR (reported = 1 AND status = 'VISIBLE'))")
                        + count("SELECT COUNT(*) FROM after_sale WHERE deleted = 0 AND status IN ('PENDING_PLATFORM','APPEALING')"));

        vo.setOrderStatuses(jdbcTemplate.query("""
                        SELECT status, COUNT(*) AS cnt FROM order_info
                         WHERE deleted = 0 GROUP BY status""",
                (rs, i) -> {
                    DashboardVO.StatusCount sc = new DashboardVO.StatusCount();
                    sc.setStatus(rs.getString("status"));
                    sc.setCount(rs.getLong("cnt"));
                    return sc;
                }));

        vo.setWeekStats(weekStats());
        return vo;
    }

    private List<DashboardVO.DayStat> weekStats() {
        List<DashboardVO.DayStat> stats = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = jdbcTemplate.queryForObject(
                    "SELECT DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? DAY), '%m-%d')", String.class, i);
            DashboardVO.DayStat stat = new DashboardVO.DayStat();
            stat.setDay(day);
            stat.setOrderCount(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM order_info
                     WHERE deleted = 0 AND DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL ? DAY)
                    """, Long.class, i));
            stat.setSales(jdbcTemplate.queryForObject("""
                    SELECT IFNULL(SUM(pay_amount), 0) FROM order_info
                     WHERE deleted = 0 AND status IN ('PAID','SHIPPED','COMPLETED')
                       AND DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL ? DAY)
                    """, BigDecimal.class, i));
            stats.add(stat);
        }
        return stats;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}
