package com.four_person.platform.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.platform.domain.model.ConfigItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigItemMapper extends BaseMapper<ConfigItem> {
}
