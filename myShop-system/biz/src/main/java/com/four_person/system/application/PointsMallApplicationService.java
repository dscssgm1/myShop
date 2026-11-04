package com.four_person.system.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.CouponClaimRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.system.api.dto.PointsMallDtos;
import com.four_person.system.domain.model.PointsExchange;
import com.four_person.system.infrastructure.persistence.mapper.PointsExchangeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 积分商城：平台/商家发布积分兑换券（coupon_template.points_price > 0），
 * 用户花积分兑换，券到「我的优惠券」。兑换先本地扣积分，再 Feign 让折扣服务发券，
 * 发券失败抛异常整体回滚返还积分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsMallApplicationService {

    private final PointsExchangeMapper exchangeMapper;
    private final MembershipApplicationService membershipApplicationService;
    private final DiscountInternalApi discountInternalApi;

    // ==================== 用户端 ====================

    /** 积分兑换券货架（跨服务读折扣服务模板，userId 用于标记限兑） */
    public List<PointsMallDtos.CouponVO> items(Long userId) {
        List<CouponTemplateVO> templates;
        try {
            templates = Results.data(discountInternalApi.pointsMallTemplates(userId));
        } catch (Exception ex) {
            log.error("积分商城读取券货架失败", ex);
            throw new BusinessException(50003, "优惠券货架读取失败，请稍后重试");
        }
        return templates.stream().map(this::toCouponVO).toList();
    }

    /**
     * 兑换：扣积分 + 折扣服务代领券。
     * 与原兑换商品同构——先本地扣积分，再 Feign 发券，失败抛异常整体回滚返还积分。
     */
    @Transactional(rollbackFor = Exception.class)
    public PointsMallDtos.ExchangeVO exchange(Long userId, PointsMallDtos.ExchangeRequest request) {
        PointsMallDtos.CouponVO template = items(userId).stream()
                .filter(t -> t.getTemplateId().equals(request.getTemplateId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(40408, "积分兑换券不存在或已下架"));
        if (template.getRemaining() == null || template.getRemaining() <= 0) {
            throw new BusinessException(40903, "该券已兑完");
        }
        if (Boolean.TRUE.equals(template.getClaimed())) {
            throw new BusinessException(40903, "已达每人限兑数量");
        }
        int pointsPrice = template.getPointsPrice() == null ? 0 : template.getPointsPrice();
        if (pointsPrice <= 0) {
            throw new BusinessException(40001, "该券不支持积分兑换");
        }

        membershipApplicationService.spend(userId, pointsPrice, "积分兑换：" + template.getTitle());

        CouponClaimRequest claimRequest = new CouponClaimRequest();
        claimRequest.setUserId(userId);
        claimRequest.setTemplateId(template.getTemplateId());
        try {
            Results.data(discountInternalApi.claimForUser(claimRequest));
        } catch (BusinessException ex) {
            throw ex; // 券侧明确失败（兑完/超限），回滚返还积分并透出原因
        } catch (Exception ex) {
            log.error("积分兑换发券失败: userId={}, templateId={}", userId, template.getTemplateId(), ex);
            throw new BusinessException(50002, "兑换失败，请稍后重试");
        }

        PointsExchange record = new PointsExchange();
        record.setUserId(userId);
        record.setItemId(template.getTemplateId());
        record.setItemName(template.getTitle());
        record.setItemEmoji("🎟️");
        record.setPoints(pointsPrice);
        exchangeMapper.insert(record);
        return toExchangeVO(record);
    }

    /** 我的兑换记录 */
    public List<PointsMallDtos.ExchangeVO> exchanges(Long userId) {
        return exchangeMapper.selectList(Wrappers.<PointsExchange>lambdaQuery()
                        .eq(PointsExchange::getUserId, userId)
                        .orderByDesc(PointsExchange::getId))
                .stream().map(this::toExchangeVO).toList();
    }

    // ==================== 内部逻辑 ====================

    private PointsMallDtos.CouponVO toCouponVO(CouponTemplateVO template) {
        PointsMallDtos.CouponVO vo = new PointsMallDtos.CouponVO();
        vo.setTemplateId(template.getId());
        vo.setTitle(template.getTitle());
        vo.setShopId(template.getShopId());
        vo.setShopName(template.getShopName());
        vo.setThresholdAmount(template.getThresholdAmount());
        vo.setDiscountAmount(template.getDiscountAmount());
        vo.setScopeText(template.getScopeText());
        vo.setPointsPrice(template.getPointsPrice());
        vo.setTotalStock(template.getTotalStock());
        vo.setRemaining(template.getRemaining());
        vo.setPerUserLimit(template.getPerUserLimit());
        vo.setClaimed(template.getClaimed());
        vo.setValidEnd(template.getValidEnd());
        return vo;
    }

    private PointsMallDtos.ExchangeVO toExchangeVO(PointsExchange record) {
        PointsMallDtos.ExchangeVO vo = new PointsMallDtos.ExchangeVO();
        vo.setId(record.getId());
        vo.setItemId(record.getItemId());
        vo.setItemName(record.getItemName());
        vo.setItemEmoji(record.getItemEmoji());
        vo.setPoints(record.getPoints());
        vo.setOrderNo(record.getOrderNo());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
