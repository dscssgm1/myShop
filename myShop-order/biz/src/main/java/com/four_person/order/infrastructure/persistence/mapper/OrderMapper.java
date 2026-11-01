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
               SET status = 'SHIPPED', ship_time = #{time}
             WHERE order_no = #{orderNo} AND status = 'PAID' AND deleted = 0
            """)
    int markShipped(@Param("orderNo") String orderNo, @Param("time") LocalDateTime time);

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
}
