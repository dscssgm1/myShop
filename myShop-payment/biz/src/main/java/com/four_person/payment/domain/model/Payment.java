package com.four_person.payment.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.four_person.common.exception.BusinessException;
import com.four_person.payment.enums.PaymentErrorCode;
import com.four_person.payment.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_order")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private String orderNo;

    /** 跨店合单订单组号；非空表示合单支付单（一次付清整组），退款按子单分摊金额部分退款 */
    private String groupNo;

    private Long userId;

    private BigDecimal amount;

    /** 已累计退款金额（合单部分退款用；>= amount 时支付单整体 REFUNDED） */
    private BigDecimal refundedAmount;

    /** ALIPAY / WECHAT / CARD */
    private String channel;

    /** PENDING / SUCCESS / CLOSED */
    private String status;

    private String tradeNo;

    private LocalDateTime paidAt;

    private LocalDateTime refundedAt;

    private String refundReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isSuccess() {
        return PaymentStatus.SUCCESS.name().equals(status);
    }

    public boolean isPending() {
        return PaymentStatus.PENDING.name().equals(status);
    }

    /** 是否还有可退余额（合单部分退款后状态仍是 SUCCESS） */
    public boolean hasRefundableBalance() {
        BigDecimal refunded = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
        return amount != null && amount.subtract(refunded).signum() > 0;
    }

    public void markPaid(LocalDateTime paidAt, String tradeNo) {
        if (!isPending()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        this.status = PaymentStatus.SUCCESS.name();
        this.paidAt = paidAt;
        this.tradeNo = tradeNo;
    }

    public void refund(LocalDateTime refundedAt, String reason) {
        if (!isSuccess()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_INVALID, "只有支付成功的支付单可以退款");
        }
        this.status = PaymentStatus.REFUNDED.name();
        this.refundedAt = refundedAt;
        this.refundReason = reason;
    }

    public void close() {
        if (isSuccess()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_INVALID, "已支付的支付单不能关闭");
        }
        if (isPending()) {
            this.status = PaymentStatus.CLOSED.name();
        }
    }

    public void reopen(BigDecimal amount, String channel) {
        this.status = PaymentStatus.PENDING.name();
        this.amount = amount;
        this.channel = channel;
        this.tradeNo = null;
        this.paidAt = null;
        this.refundedAt = null;
        this.refundReason = null;
    }
}
