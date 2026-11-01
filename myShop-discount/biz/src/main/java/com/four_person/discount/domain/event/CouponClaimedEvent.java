package com.four_person.discount.domain.event;

public record CouponClaimedEvent(Long userId, Long couponId, Long templateId) {
}
