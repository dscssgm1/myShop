package com.four_person.review.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评价模块的 API 数据结构。
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /** 提交评价：必须来自本人已完成的订单 */
    @Data
    public static class CreateRequest {
        @NotNull(message = "商品不能为空")
        private Long productId;
        @NotBlank(message = "订单号不能为空")
        private String orderNo;
        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        private Integer rating;
        /** 商品质量评分（1-5，支持半星；不传时与综合评分一致） */
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        private BigDecimal qualityRating;
        /** 服务态度评分（1-5，支持半星） */
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        private BigDecimal serviceRating;
        /** 物流速度评分（1-5，支持半星） */
        @Min(value = 1, message = "评分最低 1 星")
        @Max(value = 5, message = "评分最高 5 星")
        private BigDecimal logisticsRating;
        @NotBlank(message = "评价内容不能为空")
        private String content;
        /** 评价图片 URL，最多 9 张 */
        private List<String> images;
    }

    /** 评价详情 */
    @Data
    public static class ReviewVO {
        private Long id;
        private Long productId;
        private String productName;
        private Long shopId;
        private String orderNo;
        private Long userId;
        private String nickname;
        private Integer rating;
        /** 商品质量评分（1-5，支持半星） */
        private BigDecimal qualityRating;
        /** 服务态度评分（1-5，支持半星） */
        private BigDecimal serviceRating;
        /** 物流速度评分（1-5，支持半星） */
        private BigDecimal logisticsRating;
        private String content;
        /** 评价图片 URL */
        private List<String> images;
        /** VISIBLE 展示 / PENDING 待平台审核 / HIDDEN 已隐藏 */
        private String status;
        /** 是否被商家举报 */
        private Boolean reported;
        private String reportReason;
        private String auditReason;
        /** 商家回复 */
        private String reply;
        private LocalDateTime replyAt;
        private LocalDateTime createdAt;
    }

    /** 商品评分汇总：综合均分、三维度均分、总数、1-5 星分布 */
    @Data
    public static class SummaryVO {
        private BigDecimal avgRating;
        /** 商品质量均分 */
        private BigDecimal avgQuality;
        /** 服务态度均分 */
        private BigDecimal avgService;
        /** 物流速度均分 */
        private BigDecimal avgLogistics;
        private long total;
        private Map<Integer, Long> distribution;
    }

    /** 订单中可评价的商品 */
    @Data
    public static class ReviewableVO {
        private Long productId;
        private String productName;
        private String skuSpecs;
        private Integer quantity;
        private boolean reviewed;
    }

    /** 商家对违规评价发起审核 */
    @Data
    public static class ReportRequest {
        @NotBlank(message = "请填写举报原因")
        private String reason;
    }

    /** 商家回复评价 */
    @Data
    public static class ReplyRequest {
        @NotBlank(message = "请填写回复内容")
        @Size(max = 500, message = "回复最多 500 字")
        private String reply;
    }

    /** 平台审核动作：APPROVE 通过 / HIDE 隐藏 */
    @Data
    public static class ModerateRequest {
        @NotBlank(message = "审核动作不能为空")
        private String action;
        private String reason;
    }

    /** 管理端列表筛选 */
    @Data
    public static class AdminQuery {
        private String status;
        private Integer page;
        private Integer size;
    }

    /** 管理端统计 */
    @Data
    public static class AdminStatsVO {
        private long pending;
        private long reported;
        private long visible;
        private long hidden;
    }

}
