package com.four_person.discount.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.discount.infrastructure.persistence.po.CouponTemplatePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplatePO> {

    @Update("""
            UPDATE coupon_template
               SET claimed_count = claimed_count + 1
             WHERE id = #{id}
               AND deleted = 0
               AND status = 'ACTIVE'
               AND claimed_count < total_stock
            """)
    int increaseClaimed(@Param("id") Long id);
}
