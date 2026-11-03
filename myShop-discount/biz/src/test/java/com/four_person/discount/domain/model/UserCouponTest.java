package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.discount.enums.CouponStatus;
import com.four_person.discount.enums.DiscountErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用户券生命周期边界：到期相等边界、核销幂等、跨单核销拒绝、释放与过期。
 */
class UserCouponTest {

    private final LocalDateTime now = LocalDateTime.now();

    private UserCoupon coupon(CouponStatus status, String orderNo, LocalDateTime expiredAt) {
        return new UserCoupon(10L, 900201L, 20L, status, orderNo,
                now.minusDays(1), null, expiredAt);
    }

    @Test
    void claimFactoryCreatesUnusedCouponWithoutOrder() {
        UserCoupon coupon = UserCoupon.claim(900201L, 20L, now, now.plusDays(7));
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.UNUSED);
        assertThat(coupon.getOrderNo()).isNull();
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getExpiredAt()).isEqualTo(now.plusDays(7));
    }

    @Test
    void isUsableAtExactExpiryBoundary() {
        // 到期时刻当秒仍可用（isBefore 语义）
        assertThat(coupon(CouponStatus.UNUSED, null, now).isUsable(now)).isTrue();
        assertThat(coupon(CouponStatus.UNUSED, null, now.minusSeconds(1)).isUsable(now)).isFalse();
        // 无到期时间 = 长期有效
        assertThat(coupon(CouponStatus.UNUSED, null, null).isUsable(now)).isTrue();
        assertThat(coupon(CouponStatus.UNUSED, null, null).isExpired(now)).isFalse();
    }

    @Test
    void usedOrExpiredStatusIsNotUsable() {
        assertThat(coupon(CouponStatus.USED, "MS1", null).isUsable(now)).isFalse();
        assertThat(coupon(CouponStatus.EXPIRED, null, null).isUsable(now)).isFalse();
    }

    @Test
    void useMarksUsedWithOrderAndTime() {
        UserCoupon used = coupon(CouponStatus.UNUSED, null, now.plusDays(7)).use("MS1001", now);
        assertThat(used.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(used.getOrderNo()).isEqualTo("MS1001");
        assertThat(used.getUsedAt()).isEqualTo(now);
        assertThat(used.getId()).isEqualTo(10L);
    }

    @Test
    void useIsIdempotentForSameOrderNo() {
        UserCoupon used = coupon(CouponStatus.USED, "MS1001", null);
        assertThat(used.use("MS1001", now)).isSameAs(used);
    }

    @Test
    void useWithDifferentOrderNoThrows() {
        assertThatThrownBy(() -> coupon(CouponStatus.USED, "MS1001", null).use("MS2002", now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.COUPON_ALREADY_USED.getCode());
    }

    @Test
    void useExpiredCouponThrows() {
        assertThatThrownBy(() -> coupon(CouponStatus.UNUSED, null, now.minusSeconds(1)).use("MS1001", now))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(DiscountErrorCode.COUPON_NOT_AVAILABLE.getCode());
        assertThatThrownBy(() -> coupon(CouponStatus.EXPIRED, null, null).use("MS1001", now))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void releaseResetsToReusable() {
        UserCoupon released = coupon(CouponStatus.USED, "MS1001", now.plusDays(7)).release();
        assertThat(released.getStatus()).isEqualTo(CouponStatus.UNUSED);
        assertThat(released.getOrderNo()).isNull();
        assertThat(released.getUsedAt()).isNull();
        assertThat(released.getExpiredAt()).isEqualTo(now.plusDays(7));
    }

    @Test
    void expireKeepsHistory() {
        UserCoupon used = coupon(CouponStatus.UNUSED, null, now.minusSeconds(1))
                .use("MS1001", now.minusDays(1));
        UserCoupon expired = used.expire();
        assertThat(expired.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(expired.getOrderNo()).isEqualTo("MS1001");
        assertThat(expired.getUsedAt()).isEqualTo(now.minusDays(1));
    }
}
