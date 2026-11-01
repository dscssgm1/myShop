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
}
