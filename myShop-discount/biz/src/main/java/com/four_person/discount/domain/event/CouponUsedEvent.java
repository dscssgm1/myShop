package com.four_person.discount.domain.event;

public record CouponUsedEvent(Long userId, Long couponId, String orderNo) {
}
