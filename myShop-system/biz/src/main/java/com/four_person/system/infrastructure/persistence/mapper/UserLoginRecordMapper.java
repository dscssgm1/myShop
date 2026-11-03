package com.four_person.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.system.domain.model.UserLoginRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLoginRecordMapper extends BaseMapper<UserLoginRecord> {
}
