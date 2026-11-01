package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.MemberLevel;
import com.four_person.system.domain.model.PointsAccount;
import com.four_person.system.domain.model.PointsLog;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {

    List<MemberLevel> findAllLevels();

    Optional<PointsAccount> findAccount(Long userId);

    PointsAccount createAccount(Long userId);

    /** 原子增加积分与成长值 */
    void addPoints(Long userId, int points, int growth);

    /** 原子扣减积分，余额不足返回 false */
    boolean deductPoints(Long userId, int points);

    void saveLog(PointsLog log);

    List<PointsLog> findLogs(Long userId, int limit);
}
