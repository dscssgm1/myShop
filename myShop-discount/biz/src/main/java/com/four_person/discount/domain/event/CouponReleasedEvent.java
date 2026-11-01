package com.four_person.discount.domain.event;

public record CouponReleasedEvent(Long couponId, String orderNo) {
}
