package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跨店合单订单组（父订单）：购物车跨店结算时生成，一店铺一张子订单。
 *
 * <p>优惠（券 + 跨店满减）记在组上，按子单金额比例分摊后写进子单（尾差给最后一个子单）；
 * 支付按组一次付清；未发货取消以组为单位。</p>
 *
 * <p>状态机：UNPAID → PAID → CLOSED（全部子订单取消/退款后关组并退券）；
 * UNPAID → CANCELLED（整组取消未支付订单，券全额退回）。</p>
 */
@Data
@TableName("order_group")
public class OrderGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupNo;

    private Long userId;

    /** UNPAID / PAID / CANCELLED / CLOSED，见类注释状态机 */
    private String status;

    /** 子订单数量（店铺数） */
    private Integer shopCount;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal merchantBearAmount;

    private BigDecimal platformBearAmount;

    /** 整组抵现消耗的积分 */
    private Integer pointsUsed;

    /** 整组积分抵现金额（平台承担） */
    private BigDecimal pointsDeduction;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    private Long promotionId;

    private String promotionTitle;

    private Long couponId;

    private String couponTitle;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    /** 券核销时使用的订单号（第一张子单）；释放券必须用该单号才能命中 user_coupon.order_no */
    private String couponUseOrderNo;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private LocalDateTime closedTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isUnpaid() {
        return "UNPAID".equals(status);
    }

    public boolean isPaid() {
        return "PAID".equals(status);
    }
}
