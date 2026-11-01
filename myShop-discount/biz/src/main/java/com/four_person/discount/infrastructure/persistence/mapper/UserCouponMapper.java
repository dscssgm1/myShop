package com.four_person.discount.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.discount.infrastructure.persistence.po.UserCouponPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCouponPO> {

    @Update("""
            UPDATE user_coupon
               SET status = 'USED', order_no = #{orderNo}, used_at = #{now}
             WHERE id = #{id} AND user_id = #{userId} AND status = 'UNUSED'
            """)
    int markUsed(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("orderNo") String orderNo,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE user_coupon
               SET status = 'UNUSED', order_no = NULL, used_at = NULL
             WHERE id = #{id} AND status = 'USED' AND order_no = #{orderNo}
            """)
    int release(@Param("id") Long id, @Param("orderNo") String orderNo);

    @Update("""
            UPDATE user_coupon
               SET status = 'EXPIRED'
             WHERE id = #{id} AND status = 'UNUSED'
            """)
    int markExpired(@Param("id") Long id);
}
