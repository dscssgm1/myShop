package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.MemberDtos;
import com.four_person.system.domain.model.MemberLevel;
import com.four_person.system.domain.model.PointsAccount;
import com.four_person.system.domain.model.PointsLog;
import com.four_person.system.domain.repository.MembershipRepository;
import com.four_person.system.enums.SystemErrorCode;
import com.four_person.system.infrastructure.support.PlatformConfigReader;
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

    /** 积分规则配置组 / 消费得积分比例：每消费多少元累计 1 积分 */
    private static final String POINTS_GROUP = "points-rule";
    private static final String KEY_EARN_AMOUNT_PER_POINT = "earn-amount-per-point";

    private final MembershipRepository membershipRepository;
    private final PlatformConfigReader configReader;

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

    /** 消费获得积分与成长值，返回实际获得的积分；bizNo 记录来源订单号，便于售后扣回 */
    @Transactional
    public int earn(Long userId, BigDecimal amount, String remark, String bizNo) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        BigDecimal rate = pointsRateOf(userId);
        // 每消费 N 元得 1 积分（平台配置中心可调），再乘以会员等级积分倍率
        BigDecimal amountPerPoint = configReader.getDecimal(
                POINTS_GROUP, KEY_EARN_AMOUNT_PER_POINT, BigDecimal.ONE);
        if (amountPerPoint.signum() <= 0) {
            amountPerPoint = BigDecimal.ONE;
        }
        int gain = amount.divide(amountPerPoint, 6, RoundingMode.HALF_UP)
                .multiply(rate)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
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
        log.setBizNo(bizNo == null ? "" : bizNo);
        log.setRemark(remark == null ? "消费获得积分" : remark);
        membershipRepository.saveLog(log);
        return gain;
    }

    /**
     * 按条直发固定积分（评价奖励等场景）：乘以会员等级积分倍率，
     * 按 bizNo 幂等——同一业务单号已发放过则直接返回 0，重复调用不重复入账。
     */
    @Transactional
    public int earnFlat(Long userId, Integer points, String remark, String bizNo) {
        if (points == null || points <= 0) {
            return 0;
        }
        if (membershipRepository.sumEarnedByBizNo(userId, bizNo) > 0) {
            return 0;
        }
        BigDecimal rate = pointsRateOf(userId);
        int gain = BigDecimal.valueOf(points).multiply(rate)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
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
        log.setBizNo(bizNo == null ? "" : bizNo);
        log.setRemark(remark == null ? "评价奖励积分" : remark);
        membershipRepository.saveLog(log);
        return gain;
    }

    /**
     * 售后退款扣回该订单发放的积分。
     * 已发放合计与当前余额取小者扣减（积分可能已被消费，不足部分不追讨），成长值不回收。
     */
    @Transactional
    public int refundEarn(Long userId, String bizNo, String remark) {
        int earned = membershipRepository.sumEarnedByBizNo(userId, bizNo);
        if (earned <= 0) {
            return 0;
        }
        PointsAccount account = accountOf(userId);
        int balance = account.getPoints() == null ? 0 : account.getPoints();
        int deduct = Math.min(earned, Math.max(balance, 0));
        if (deduct <= 0) {
            return 0;
        }
        if (!membershipRepository.deductPoints(userId, deduct)) {
            return 0;
        }
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChangeAmount(-deduct);
        log.setBalance(accountOf(userId).getPoints());
        log.setType("DEDUCT");
        log.setBizNo(bizNo);
        log.setRemark((remark == null ? "售后扣回积分" : remark)
                + (deduct < earned ? "（应扣 " + earned + "，余额不足已扣 " + deduct + "）" : ""));
        membershipRepository.saveLog(log);
        return deduct;
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

    // ==================== 平台配置中心：会员等级 ====================

    public List<MemberDtos.MemberLevelVO> levels() {
        return membershipRepository.findAllLevels().stream().map(this::toLevelVO).toList();
    }

    @Transactional
    public MemberDtos.MemberLevelVO createLevel(MemberDtos.MemberLevelRequest request) {
        validateGrowth(request.getMinGrowth(), null);
        MemberLevel level = new MemberLevel();
        applyLevel(level, request);
        membershipRepository.saveLevel(level);
        return toLevelVO(level);
    }

    @Transactional
    public MemberDtos.MemberLevelVO updateLevel(Long id, MemberDtos.MemberLevelRequest request) {
        MemberLevel level = requireLevel(id);
        validateGrowth(request.getMinGrowth(), id);
        applyLevel(level, request);
        membershipRepository.saveLevel(level);
        return toLevelVO(level);
    }

    @Transactional
    public void deleteLevel(Long id) {
        requireLevel(id);
        membershipRepository.deleteLevel(id);
    }

    private void validateGrowth(Integer minGrowth, Long excludeId) {
        boolean duplicated = membershipRepository.findAllLevels().stream()
                .anyMatch(level -> level.getMinGrowth().equals(minGrowth)
                        && (excludeId == null || !excludeId.equals(level.getId())));
        if (duplicated) {
            throw new BusinessException(40902, "成长值门槛与其它等级重复");
        }
    }

    private MemberLevel requireLevel(Long id) {
        return membershipRepository.findLevel(id)
                .orElseThrow(() -> new BusinessException(40405, "会员等级不存在"));
    }

    private void applyLevel(MemberLevel level, MemberDtos.MemberLevelRequest request) {
        level.setName(request.getName().trim());
        level.setMinGrowth(request.getMinGrowth());
        level.setPointsRate(request.getPointsRate());
        level.setDiscount(request.getDiscount());
        level.setFreeShipping(Boolean.TRUE.equals(request.getFreeShipping()) ? 1 : 0);
        level.setSort(request.getSort() == null ? 0 : request.getSort());
    }

    private MemberDtos.MemberLevelVO toLevelVO(MemberLevel level) {
        MemberDtos.MemberLevelVO vo = new MemberDtos.MemberLevelVO();
        vo.setId(level.getId());
        vo.setName(level.getName());
        vo.setMinGrowth(level.getMinGrowth());
        vo.setPointsRate(level.getPointsRate());
        vo.setDiscount(level.getDiscount());
        vo.setFreeShipping(level.getFreeShipping() != null && level.getFreeShipping() == 1);
        vo.setSort(level.getSort());
        return vo;
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
