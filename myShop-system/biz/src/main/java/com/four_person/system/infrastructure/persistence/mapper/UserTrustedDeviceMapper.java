package com.four_person.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.system.domain.model.UserTrustedDevice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTrustedDeviceMapper extends BaseMapper<UserTrustedDevice> {
}
