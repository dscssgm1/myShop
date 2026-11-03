package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.UserLoginRecord;

import java.util.List;

public interface LoginRecordRepository {

    UserLoginRecord save(UserLoginRecord record);

    /** 最近 limit 条登录记录，按时间倒序 */
    List<UserLoginRecord> findRecent(Long userId, int limit);
}
