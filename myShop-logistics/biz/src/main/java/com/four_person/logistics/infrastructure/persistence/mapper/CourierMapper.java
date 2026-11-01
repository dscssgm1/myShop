package com.four_person.logistics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.logistics.domain.model.Courier;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourierMapper extends BaseMapper<Courier> {
}
