package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.system.domain.model.UserLoginRecord;
import com.four_person.system.domain.repository.LoginRecordRepository;
import com.four_person.system.infrastructure.persistence.mapper.UserLoginRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class LoginRecordRepositoryImpl implements LoginRecordRepository {

    private final UserLoginRecordMapper mapper;

    @Override
    public UserLoginRecord save(UserLoginRecord record) {
        mapper.insert(record);
        return record;
    }

    @Override
    public List<UserLoginRecord> findRecent(Long userId, int limit) {
        return mapper.selectList(Wrappers.<UserLoginRecord>lambdaQuery()
                .eq(UserLoginRecord::getUserId, userId)
                .orderByDesc(UserLoginRecord::getId)
                .last("LIMIT " + limit));
    }
}
