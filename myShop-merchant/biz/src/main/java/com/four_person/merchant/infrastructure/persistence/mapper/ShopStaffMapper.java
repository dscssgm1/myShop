package com.four_person.merchant.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.merchant.domain.model.ShopStaff;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShopStaffMapper extends BaseMapper<ShopStaff> {
}
