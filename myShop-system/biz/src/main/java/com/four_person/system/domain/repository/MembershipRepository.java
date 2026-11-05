package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.MemberLevel;
import com.four_person.system.domain.model.PointsAccount;
import com.four_person.system.domain.model.PointsLog;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {

    List<MemberLevel> findAllLevels();

    Optional<MemberLevel> findLevel(Long id);

    void saveLevel(MemberLevel level);

    void deleteLevel(Long id);

    Optional<PointsAccount> findAccount(Long userId);

    PointsAccount createAccount(Long userId);

    /** 原子增加积分与成长值 */
    void addPoints(Long userId, int points, int growth);

    /** 原子扣减积分，余额不足返回 false */
    boolean deductPoints(Long userId, int points);

    void saveLog(PointsLog log);

    List<PointsLog> findLogs(Long userId, int limit);

    /** 按业务单号合计已发放的积分（售后扣回用） */
    int sumEarnedByBizNo(Long userId, String bizNo);

    /** 按业务单号合计已扣回的积分（删除评价等场景幂等判断用），返回负数或 0 */
    int sumDeductedByBizNo(Long userId, String bizNo);

    /** 按业务单号合计已扣减的积分（抵现退款定位原扣减用） */
    int sumSpentByBizNo(Long userId, String bizNo);

    /** 按业务单号是否已有退回流水（抵现退款幂等用） */
    int sumRefundedByBizNo(Long userId, String bizNo);
}
