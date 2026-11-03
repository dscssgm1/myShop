package com.four_person.review.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品评价。
 * <p>
 * 只有已完成订单中的买家可以提交；商家与平台可对违规评价发起审核与隐藏。
 */
@Data
@TableName("review")
public class Review {

    /** 正常展示 */
    public static final String STATUS_VISIBLE = "VISIBLE";
    /** 新账号 / 低消费账号提交，待平台审核后展示 */
    public static final String STATUS_PENDING = "PENDING";
    /** 平台审核后隐藏，不计入评分 */
    public static final String STATUS_HIDDEN = "HIDDEN";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private String productName;
    private Long shopId;
    private String orderNo;
    private Long userId;
    private String nickname;
    private Integer rating;
    /** 商品质量评分（1-5，支持半星；空视为与综合分一致） */
    private BigDecimal qualityRating;
    /** 服务态度评分（1-5，支持半星） */
    private BigDecimal serviceRating;
    /** 物流速度评分（1-5，支持半星） */
    private BigDecimal logisticsRating;
    private String content;
    /** 评价图片 URL JSON 数组 */
    private String images;

    /** 商家回复 */
    private String reply;
    private LocalDateTime replyAt;

    /** VISIBLE / PENDING / HIDDEN */
    private String status;

    /** 商家是否已举报 */
    private Integer reported;
    private String reportReason;
    private Long reportBy;
    private LocalDateTime reportedAt;

    /** 平台审核结果 */
    private String auditReason;
    private Long auditedBy;
    private LocalDateTime auditedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
