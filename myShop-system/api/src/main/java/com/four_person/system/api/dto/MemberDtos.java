package com.four_person.system.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        /** 关联业务单号（订单号），用于售后扣回按单定位 */
        private String bizNo;
    }

    /** 按条直发固定积分（评价奖励等场景），bizNo 幂等 */
    @Data
    public static class EarnFlatRequest {

        private Long userId;
        /** 发放积分数（业务侧配置的固定值） */
        private Integer points;
        private String remark;
        /** 业务单号（如 REVIEW-123），重复发放按此幂等 */
        private String bizNo;
    }

    /** 会员等级（平台配置中心可维护） */
    @Data
    public static class MemberLevelVO {

        private Long id;
        private String name;
        /** 达到该等级所需成长值 */
        private Integer minGrowth;
        /** 积分倍率 */
        private BigDecimal pointsRate;
        /** 会员折扣，9.50 表示 9.5 折 */
        private BigDecimal discount;
        private Boolean freeShipping;
        private Integer sort;
    }

    @Data
    public static class MemberLevelRequest {

        @NotBlank(message = "等级名称不能为空")
        private String name;

        @NotNull(message = "成长值门槛不能为空")
        @Min(value = 0, message = "成长值门槛不能为负数")
        private Integer minGrowth;

        @NotNull(message = "积分倍率不能为空")
        @DecimalMin(value = "0.1", message = "积分倍率至少为 0.1")
        private BigDecimal pointsRate;

        @NotNull(message = "会员折扣不能为空")
        @DecimalMin(value = "0.1", message = "会员折扣至少为 0.1 折")
        @DecimalMax(value = "10", message = "会员折扣最多为 10 折")
        private BigDecimal discount;

        private Boolean freeShipping;

        private Integer sort;
    }
}
