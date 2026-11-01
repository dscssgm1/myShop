package com.four_person.system.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员与积分契约。
 */
public final class MemberDtos {

    private MemberDtos() {
    }

    @Data
    public static class MemberProfileVO {

        private Long userId;
        private String levelName;
        private Integer points;
        private Integer growth;
        /** 下一等级所需成长值，已是最高等级时为 null */
        private Integer nextLevelGrowth;
        private BigDecimal pointsRate;
        /** 会员折扣，9.50 表示 9.5 折 */
        private BigDecimal discount;
        private Boolean freeShipping;
    }

    @Data
    public static class PointsLogVO {

        private Long id;
        private Integer changeAmount;
        private Integer balance;
        private String type;
        private String remark;
        private LocalDateTime createdAt;
    }

    @Data
    public static class EarnPointsRequest {

        private Long userId;
        /** 消费金额（元） */
        private BigDecimal amount;
        private String remark;
    }
}
