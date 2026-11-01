package com.four_person.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.system.domain.model.PointsAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointsAccountMapper extends BaseMapper<PointsAccount> {

    /**
     * 原子增加积分与成长值。
     */
    @Update("""
            UPDATE sys_points_account
               SET points = points + #{points},
                   growth = growth + #{growth}
             WHERE user_id = #{userId}
            """)
    int addPoints(@Param("userId") Long userId, @Param("points") int points, @Param("growth") int growth);

    /**
     * 原子扣减积分，余额不足时不更新并返回 0。
     */
    @Update("""
            UPDATE sys_points_account
               SET points = points - #{points}
             WHERE user_id = #{userId}
               AND points >= #{points}
            """)
    int deductPoints(@Param("userId") Long userId, @Param("points") int points);
}
