package com.four_person.platform.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.platform.domain.model.Region;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegionMapper extends BaseMapper<Region> {
}
