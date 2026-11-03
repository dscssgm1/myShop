package com.four_person.platform.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardVO {

    private long todayOrderCount;
    private BigDecimal todaySales;
    private long totalUsers;
    private long totalProducts;
    private long totalShops;
    private long pendingAudits;

    /** 各状态订单数 */
    private List<StatusCount> orderStatuses;

    /** 近 7 日订单与销售额 */
    private List<DayStat> weekStats;

    @Data
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    public static class DayStat {
        private String day;
        private long orderCount;
        private BigDecimal sales;
    }
}
