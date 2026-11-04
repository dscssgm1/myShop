package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.enums.OrderErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单聚合根，负责订单状态机与金额快照。
 */
@Data
@TableName("order_info")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    /** 见 {@link OrderStatus} */
    private String status;

    /** NORMAL 普通订单 / RECOVERY 售后补款订单 */
    private String bizType;

    /** 补款订单对应的原订单号 */
    private String sourceOrderNo;

    /** 补款订单对应的售后单 */
    private Long afterSaleId;

    /** 跨店合单订单组号；非空表示合单子订单，取消/支付按组处理 */
    private String groupNo;

    /** 所属店铺：一店一单 */
    private Long shopId;

    private String shopName;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    /** 优惠中商家承担的部分（活动比例 + 券归属分摊） */
    private BigDecimal merchantBearAmount;

    /** 优惠中平台承担（补贴）的部分 */
    private BigDecimal platformBearAmount;

    /** 本单抵现消耗的积分 */
    private Integer pointsUsed;

    /** 本单积分抵现金额（平台承担，已含在 platformBearAmount 内） */
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

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    /** 物流正向运单签收时间，作为自动确认收货的起算点 */
    private LocalDateTime signedTime;

    private LocalDateTime completeTime;

    private LocalDateTime cancelTime;

    private LocalDateTime refundTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isPaid() {
        return OrderStatus.isPaid(status);
    }

    public void pay(LocalDateTime paidAt) {
        ensureStatus(OrderStatus.UNPAID, "只有待付款订单可以支付");
        this.status = OrderStatus.PAID.name();
        this.payTime = paidAt;
    }

    public void ship(LocalDateTime shippedAt) {
        ensureStatus(OrderStatus.PAID, "只有待发货订单可以发货");
        this.status = OrderStatus.SHIPPED.name();
        this.shipTime = shippedAt;
    }

    public void complete(LocalDateTime completedAt) {
        ensureStatus(OrderStatus.SHIPPED, "只有待收货订单可以确认收货");
        this.status = OrderStatus.COMPLETED.name();
        this.completeTime = completedAt;
    }

    public void cancel(LocalDateTime cancelledAt) {
        ensureStatus(OrderStatus.UNPAID, "只有待付款订单可以取消");
        this.status = OrderStatus.CANCELLED.name();
        this.cancelTime = cancelledAt;
    }

    /** 商家发货前，买家取消已支付订单并退款 */
    public void refundBeforeShip(LocalDateTime refundedAt) {
        ensureStatus(OrderStatus.PAID, "商家已发货，请通过售后处理");
        this.status = OrderStatus.REFUNDED.name();
        this.refundTime = refundedAt;
        this.cancelTime = refundedAt;
    }

    /** 发起售后：订单进入售后中，售后结束后可恢复原状态 */
    public void requestAfterSale() {
        if (!OrderStatus.SHIPPED.name().equals(status) && !OrderStatus.COMPLETED.name().equals(status)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单发货后才能申请售后");
        }
        this.status = OrderStatus.AFTER_SALE.name();
    }

    public void markRefunded(LocalDateTime refundedAt) {
        ensureStatus(OrderStatus.AFTER_SALE, "只有售后中的订单可以退款");
        this.status = OrderStatus.REFUNDED.name();
        this.refundTime = refundedAt;
    }

    /** 售后被商家/平台拒绝后恢复订单原状态 */
    public void restoreStatus(String originalStatus) {
        if (!OrderStatus.AFTER_SALE.name().equals(status)) {
            return;
        }
        if (!OrderStatus.SHIPPED.name().equals(originalStatus)
                && !OrderStatus.COMPLETED.name().equals(originalStatus)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单原状态异常");
        }
        this.status = originalStatus;
    }

    public boolean isRecovery() {
        return "RECOVERY".equals(bizType);
    }

    public void ensureStatus(OrderStatus expected, String message) {
        if (!expected.name().equals(status)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, message);
        }
    }
}
