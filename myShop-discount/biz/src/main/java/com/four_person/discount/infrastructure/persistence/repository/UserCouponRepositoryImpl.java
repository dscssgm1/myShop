package com.four_person.discount.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.discount.domain.model.UserCoupon;
import com.four_person.discount.domain.repository.UserCouponRepository;
import com.four_person.discount.enums.CouponStatus;
import com.four_person.discount.infrastructure.persistence.mapper.UserCouponMapper;
import com.four_person.discount.infrastructure.persistence.po.UserCouponPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponMapper userCouponMapper;

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return userCouponMapper.selectList(Wrappers.<UserCouponPO>lambdaQuery()
                        .eq(UserCouponPO::getUserId, userId)
                        .orderByDesc(UserCouponPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserCoupon> findByUserIdAndStatus(Long userId, CouponStatus status) {
        return userCouponMapper.selectList(Wrappers.<UserCouponPO>lambdaQuery()
                        .eq(UserCouponPO::getUserId, userId)
                        .eq(UserCouponPO::getStatus, status.name())
                        .orderByDesc(UserCouponPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(userCouponMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public int countByUserAndTemplate(Long userId, Long templateId) {
        return Math.toIntExact(userCouponMapper.selectCount(Wrappers.<UserCouponPO>lambdaQuery()
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getTemplateId, templateId)));
    }

    @Override
    public UserCoupon save(UserCoupon coupon) {
        UserCouponPO po = toPO(coupon);
        if (po.getId() == null) {
            userCouponMapper.insert(po);
            return coupon.withId(po.getId());
        }
        userCouponMapper.updateById(po);
        return coupon;
    }

    @Override
    public boolean markUsed(Long id, Long userId, String orderNo, LocalDateTime now) {
        return userCouponMapper.markUsed(id, userId, orderNo, now) > 0;
    }

    @Override
    public boolean release(Long id, String orderNo) {
        return userCouponMapper.release(id, orderNo) > 0;
    }

    @Override
    public boolean markExpired(Long id) {
        return userCouponMapper.markExpired(id) > 0;
    }

    private UserCoupon toDomain(UserCouponPO po) {
        return new UserCoupon(po.getId(), po.getUserId(), po.getTemplateId(),
                CouponStatus.valueOf(po.getStatus()), po.getOrderNo(),
                po.getClaimedAt(), po.getUsedAt(), po.getExpiredAt());
    }

    private UserCouponPO toPO(UserCoupon coupon) {
        UserCouponPO po = new UserCouponPO();
        po.setId(coupon.getId());
        po.setUserId(coupon.getUserId());
        po.setTemplateId(coupon.getTemplateId());
        po.setStatus(coupon.getStatus().name());
        po.setOrderNo(coupon.getOrderNo());
        po.setClaimedAt(coupon.getClaimedAt());
        po.setUsedAt(coupon.getUsedAt());
        po.setExpiredAt(coupon.getExpiredAt());
        return po;
    }
}
