package com.four_person.discount.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("promotion_enrollment")
public class PromotionEnrollmentPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long promotionId;

    private Long shopId;

    private String shopName;

    private Long productId;

    private String productName;

    private String status;

    private String reason;

    private LocalDateTime appliedAt;

    /** 驳回重报会把审核时间复位为空，须强制参与 UPDATE，避免残留上次审核时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reviewedAt;
}
