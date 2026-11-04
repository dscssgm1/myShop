package com.four_person.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.order.domain.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("""
            UPDATE order_info
               SET status = 'PAID', pay_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'UNPAID' AND deleted = 0
            """)
    int markPaid(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET status = 'PAID', pay_time = #{time}
             WHERE group_no = #{groupNo} AND status = 'UNPAID' AND deleted = 0
            """)
    int markGroupChildrenPaid(@Param("groupNo") String groupNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET status = 'SHIPPED', ship_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'PAID' AND deleted = 0
            """)
    int markShipped(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET signed_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'SHIPPED' AND signed_time IS NULL AND deleted = 0
            """)
    int markSigned(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET status = 'COMPLETED', complete_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'SHIPPED' AND deleted = 0
            """)
    int markCompleted(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET status = 'CANCELLED', cancel_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'UNPAID' AND deleted = 0
            """)
    int markCancelled(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET status = 'AFTER_SALE'
             WHERE order_no = #{orderNo} AND status IN ('SHIPPED', 'COMPLETED') AND deleted = 0
            """)
    int markAfterSale(@Param("orderNo") String orderNo);

    @Update("""
            UPDATE order_info
               SET status = #{status}
             WHERE order_no = #{orderNo} AND status = 'AFTER_SALE' AND deleted = 0
            """)
    int restoreStatus(@Param("orderNo") String orderNo, @Param("status") String status);

    @Update("""
            UPDATE order_info
               SET status = 'REFUNDED', refund_time = #{time}, cancel_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'PAID' AND deleted = 0
            """)
    int markRefundedAfterCancel(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_info
               SET status = 'REFUNDED', refund_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'AFTER_SALE' AND deleted = 0
            """)
    int markRefundedAfterSale(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);
}
