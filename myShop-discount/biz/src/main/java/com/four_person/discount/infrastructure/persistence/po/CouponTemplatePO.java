package com.four_person.discount.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon_template")
public class CouponTemplatePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 0 平台券；>0 商家券 */
    private Long shopId;

    private String title;

    private BigDecimal thresholdAmount;

    private BigDecimal discountAmount;

    private String scopeType;

    private String scopeIds;

    private Integer totalStock;

    private Integer claimedCount;

    private Integer perUserLimit;

    /** 是否可与促销活动同用：1 是 0 否 */
    private Integer couponStackable;

    /** 是否可与其他优惠券叠加：1 是 0 否 */
    private Integer stackable;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
