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

    private Long userId;

    private BigDecimal amount;

    /** ALIPAY / WECHAT / CARD */
    private String channel;

    /** PENDING / SUCCESS / CLOSED */
    private String status;

    private String tradeNo;

    private LocalDateTime paidAt;

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

    public void markPaid(LocalDateTime paidAt, String tradeNo) {
        if (!isPending()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        this.status = PaymentStatus.SUCCESS.name();
        this.paidAt = paidAt;
        this.tradeNo = tradeNo;
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
    }
}
