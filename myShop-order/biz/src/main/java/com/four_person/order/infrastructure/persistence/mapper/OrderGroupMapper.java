package com.four_person.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.order.domain.model.OrderGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderGroupMapper extends BaseMapper<OrderGroup> {

    @Update("""
            UPDATE order_group
               SET status = 'PAID', pay_time = #{time}
             WHERE group_no = #{groupNo} AND status = 'UNPAID' AND deleted = 0
            """)
    int markPaid(@Param("groupNo") String groupNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_group
               SET status = 'CANCELLED', cancel_time = #{time}
             WHERE group_no = #{groupNo} AND status = 'UNPAID' AND deleted = 0
            """)
    int markCancelled(@Param("groupNo") String groupNo, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE order_group
               SET status = 'CLOSED', closed_time = #{time}
             WHERE group_no = #{groupNo} AND status = 'PAID' AND deleted = 0
            """)
    int markClosed(@Param("groupNo") String groupNo, @Param("time") LocalDateTime time);
}
