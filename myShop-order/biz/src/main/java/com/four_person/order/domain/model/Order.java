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

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

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

    private LocalDateTime completeTime;

    private LocalDateTime cancelTime;

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

    public void ensureStatus(OrderStatus expected, String message) {
        if (!expected.name().equals(status)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, message);
        }
    }
}
