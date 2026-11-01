package com.four_person.discount.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.discount.infrastructure.persistence.po.PromotionPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromotionMapper extends BaseMapper<PromotionPO> {
}
