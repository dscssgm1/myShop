package com.four_person.discount.domain.repository;

import com.four_person.discount.domain.model.UserCoupon;
import com.four_person.discount.enums.CouponStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    List<UserCoupon> findByUserId(Long userId);

    List<UserCoupon> findByUserIdAndStatus(Long userId, CouponStatus status);

    Optional<UserCoupon> findById(Long id);

    int countByUserAndTemplate(Long userId, Long templateId);

    UserCoupon save(UserCoupon coupon);

    boolean markUsed(Long id, Long userId, String orderNo, LocalDateTime now);

    boolean release(Long id, String orderNo);

    boolean markExpired(Long id);
}
