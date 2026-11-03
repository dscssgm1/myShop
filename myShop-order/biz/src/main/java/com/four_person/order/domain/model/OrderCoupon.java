package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单 × 优惠券核销明细（一单多券）。
 * 记录本单实际用了哪些券、各抵扣多少，取消订单时据此逐一释放。
 */
@Data
@TableName("order_coupon")
public class OrderCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** user_coupon.id */
    private Long couponId;

    /** 券标题快照 */
    private String title;

    /** 本单实际抵扣金额 */
    private BigDecimal discount;
}
