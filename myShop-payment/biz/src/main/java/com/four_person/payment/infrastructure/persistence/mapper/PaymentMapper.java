package com.four_person.payment.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.payment.domain.model.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Update("""
            UPDATE payment_order
               SET status = 'SUCCESS', trade_no = #{tradeNo}, paid_at = #{paidAt}
             WHERE payment_no = #{paymentNo} AND status = 'PENDING' AND deleted = 0
            """)
    int markPaid(@Param("paymentNo") String paymentNo,
                 @Param("tradeNo") String tradeNo,
                 @Param("paidAt") LocalDateTime paidAt);

    @Update("""
            UPDATE payment_order
               SET status = 'CLOSED'
             WHERE payment_no = #{paymentNo} AND status = 'PENDING' AND deleted = 0
            """)
    int markClosed(@Param("paymentNo") String paymentNo);

    @Update("""
            UPDATE payment_order
               SET status = 'REFUNDED', refunded_at = #{refundedAt}, refund_reason = #{reason}
             WHERE payment_no = #{paymentNo} AND status = 'SUCCESS' AND deleted = 0
            """)
    int markRefunded(@Param("paymentNo") String paymentNo,
                     @Param("refundedAt") LocalDateTime refundedAt,
                     @Param("reason") String reason);

    /** 合单部分退款：累计已退金额，退满整单金额时支付单置为 REFUNDED */
    @Update("""
            UPDATE payment_order
               SET refunded_at = #{refundedAt}, refund_reason = #{reason},
                   refunded_amount = refunded_amount + #{delta},
                   status = IF(refunded_amount + #{delta} >= amount, 'REFUNDED', 'SUCCESS')
             WHERE payment_no = #{paymentNo} AND status = 'SUCCESS' AND deleted = 0
            """)
    int markPartialRefund(@Param("paymentNo") String paymentNo,
                          @Param("delta") java.math.BigDecimal delta,
                          @Param("refundedAt") LocalDateTime refundedAt,
                          @Param("reason") String reason);
}
