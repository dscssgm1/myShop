package com.four_person.message.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.message.domain.model.MessageSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageSessionMapper extends BaseMapper<MessageSession> {
}
