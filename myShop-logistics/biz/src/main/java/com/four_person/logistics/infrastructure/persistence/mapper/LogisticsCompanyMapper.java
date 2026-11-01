package com.four_person.logistics.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.logistics.domain.model.LogisticsCompany;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogisticsCompanyMapper extends BaseMapper<LogisticsCompany> {
}
