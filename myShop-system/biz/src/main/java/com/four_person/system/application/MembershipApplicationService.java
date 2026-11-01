package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.MemberDtos;
import com.four_person.system.domain.model.MemberLevel;
import com.four_person.system.domain.model.PointsAccount;
import com.four_person.system.domain.model.PointsLog;
import com.four_person.system.domain.repository.MembershipRepository;
import com.four_person.system.enums.SystemErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * 会员等级与积分。
 * <p>
 * 等级按成长值匹配；积分获取 = 消费金额 × 当前等级积分倍率。
 */
@Service
@RequiredArgsConstructor
public class MembershipApplicationService {

    private final MembershipRepository membershipRepository;

    public MemberDtos.MemberProfileVO profile(Long userId) {
        PointsAccount account = accountOf(userId);
        List<MemberLevel> levels = membershipRepository.findAllLevels();

        MemberLevel current = levels.stream()
                .filter(level -> account.getGrowth() >= level.getMinGrowth())
                .max(Comparator.comparingInt(MemberLevel::getMinGrowth))
                .orElse(levels.isEmpty() ? null : levels.getFirst());
        Integer nextGrowth = levels.stream()
                .filter(level -> level.getMinGrowth() > account.getGrowth())
                .map(MemberLevel::getMinGrowth)
                .min(Integer::compareTo)
                .orElse(null);

        MemberDtos.MemberProfileVO vo = new MemberDtos.MemberProfileVO();
        vo.setUserId(userId);
        vo.setPoints(account.getPoints());
        vo.setGrowth(account.getGrowth());
        vo.setNextLevelGrowth(nextGrowth);
        if (current != null) {
            vo.setLevelName(current.getName());
            vo.setPointsRate(current.getPointsRate());
            vo.setDiscount(current.getDiscount());
            vo.setFreeShipping(current.getFreeShipping() != null && current.getFreeShipping() == 1);
        }
        return vo;
    }

    public List<MemberDtos.PointsLogVO> logs(Long userId, int limit) {
        return membershipRepository.findLogs(userId, limit).stream().map(this::toVO).toList();
    }

    /** 消费获得积分与成长值，返回实际获得的积分 */
    @Transactional
    public int earn(Long userId, BigDecimal amount, String remark) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        BigDecimal rate = pointsRateOf(userId);
        int gain = amount.multiply(rate).setScale(0, RoundingMode.DOWN).intValue();
        if (gain <= 0) {
            return 0;
        }
        accountOf(userId);
        membershipRepository.addPoints(userId, gain, gain);

        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChangeAmount(gain);
        log.setBalance(accountOf(userId).getPoints());
        log.setType("EARN");
        log.setRemark(remark == null ? "消费获得积分" : remark);
        membershipRepository.saveLog(log);
        return gain;
    }

    /** 扣减积分（积分商城兑换等场景） */
    @Transactional
    public void spend(Long userId, int points, String remark) {
        if (points <= 0) {
            return;
        }
        if (!membershipRepository.deductPoints(userId, points)) {
            throw new BusinessException(SystemErrorCode.POINTS_INSUFFICIENT);
        }
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChangeAmount(-points);
        log.setBalance(accountOf(userId).getPoints());
        log.setType("SPEND");
        log.setRemark(remark == null ? "积分消费" : remark);
        membershipRepository.saveLog(log);
    }

    private PointsAccount accountOf(Long userId) {
        return membershipRepository.findAccount(userId)
                .orElseGet(() -> membershipRepository.createAccount(userId));
    }

    private BigDecimal pointsRateOf(Long userId) {
        PointsAccount account = accountOf(userId);
        return membershipRepository.findAllLevels().stream()
                .filter(level -> account.getGrowth() >= level.getMinGrowth())
                .max(Comparator.comparingInt(MemberLevel::getMinGrowth))
                .map(MemberLevel::getPointsRate)
                .orElse(BigDecimal.ONE);
    }

    private MemberDtos.PointsLogVO toVO(PointsLog log) {
        MemberDtos.PointsLogVO vo = new MemberDtos.PointsLogVO();
        vo.setId(log.getId());
        vo.setChangeAmount(log.getChangeAmount());
        vo.setBalance(log.getBalance());
        vo.setType(log.getType());
        vo.setRemark(log.getRemark());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
