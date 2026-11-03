package com.four_person.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.order.domain.model.CommissionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommissionRecordMapper extends BaseMapper<CommissionRecord> {
}
