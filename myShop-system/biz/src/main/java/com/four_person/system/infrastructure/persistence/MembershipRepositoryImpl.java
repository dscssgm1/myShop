package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.system.domain.model.MemberLevel;
import com.four_person.system.domain.model.PointsAccount;
import com.four_person.system.domain.model.PointsLog;
import com.four_person.system.domain.repository.MembershipRepository;
import com.four_person.system.infrastructure.persistence.mapper.MemberLevelMapper;
import com.four_person.system.infrastructure.persistence.mapper.PointsAccountMapper;
import com.four_person.system.infrastructure.persistence.mapper.PointsLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MembershipRepositoryImpl implements MembershipRepository {

    private final MemberLevelMapper levelMapper;
    private final PointsAccountMapper accountMapper;
    private final PointsLogMapper logMapper;

    @Override
    public List<MemberLevel> findAllLevels() {
        return levelMapper.selectList(Wrappers.<MemberLevel>lambdaQuery()
                .orderByAsc(MemberLevel::getMinGrowth)
                .orderByAsc(MemberLevel::getId));
    }

    @Override
    public Optional<MemberLevel> findLevel(Long id) {
        return Optional.ofNullable(levelMapper.selectById(id));
    }

    @Override
    public void saveLevel(MemberLevel level) {
        if (level.getId() == null) {
            levelMapper.insert(level);
        } else {
            levelMapper.updateById(level);
        }
    }

    @Override
    public void deleteLevel(Long id) {
        levelMapper.deleteById(id);
    }

    @Override
    public Optional<PointsAccount> findAccount(Long userId) {
        return Optional.ofNullable(accountMapper.selectOne(
                Wrappers.<PointsAccount>lambdaQuery().eq(PointsAccount::getUserId, userId)));
    }

    @Override
    public PointsAccount createAccount(Long userId) {
        PointsAccount account = new PointsAccount();
        account.setUserId(userId);
        account.setPoints(0);
        account.setGrowth(0);
        accountMapper.insert(account);
        return account;
    }

    @Override
    public void addPoints(Long userId, int points, int growth) {
        accountMapper.addPoints(userId, points, growth);
    }

    @Override
    public boolean deductPoints(Long userId, int points) {
        return accountMapper.deductPoints(userId, points) > 0;
    }

    @Override
    public int sumEarnedByBizNo(Long userId, String bizNo) {
        List<PointsLog> logs = logMapper.selectList(Wrappers.<PointsLog>lambdaQuery()
                .eq(PointsLog::getUserId, userId)
                .eq(PointsLog::getBizNo, bizNo)
                .eq(PointsLog::getType, "EARN"));
        return logs.stream().mapToInt(log -> log.getChangeAmount() == null ? 0 : log.getChangeAmount()).sum();
    }

    @Override
    public int sumSpentByBizNo(Long userId, String bizNo) {
        List<PointsLog> logs = logMapper.selectList(Wrappers.<PointsLog>lambdaQuery()
                .eq(PointsLog::getUserId, userId)
                .eq(PointsLog::getBizNo, bizNo)
                .eq(PointsLog::getType, "SPEND"));
        return logs.stream().mapToInt(log -> log.getChangeAmount() == null ? 0 : log.getChangeAmount()).sum();
    }

    @Override
    public int sumRefundedByBizNo(Long userId, String bizNo) {
        List<PointsLog> logs = logMapper.selectList(Wrappers.<PointsLog>lambdaQuery()
                .eq(PointsLog::getUserId, userId)
                .eq(PointsLog::getBizNo, bizNo)
                .eq(PointsLog::getType, "REFUND"));
        return logs.stream().mapToInt(log -> log.getChangeAmount() == null ? 0 : log.getChangeAmount()).sum();
    }

    @Override
    public void saveLog(PointsLog log) {
        logMapper.insert(log);
    }

    @Override
    public List<PointsLog> findLogs(Long userId, int limit) {
        Page<PointsLog> page = logMapper.selectPage(new Page<>(1, Math.max(limit, 1)),
                Wrappers.<PointsLog>lambdaQuery()
                        .eq(PointsLog::getUserId, userId)
                        .orderByDesc(PointsLog::getId));
        return page.getRecords();
    }
}
