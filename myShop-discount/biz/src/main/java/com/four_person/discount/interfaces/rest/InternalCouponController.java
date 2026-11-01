package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.application.CouponApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供订单服务调用的优惠券核销/释放接口。
 */
@RestController
@RequestMapping("/internal/coupons")
@RequiredArgsConstructor
public class InternalCouponController {

    private final CouponApplicationService couponApplicationService;

    @PostMapping("/{id}/use")
    public Result<Void> use(@PathVariable Long id, @Valid @RequestBody CouponUseRequest request) {
        couponApplicationService.useCoupon(request.getUserId(), id, request.getOrderNo());
        return Result.success();
    }

    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @Valid @RequestBody CouponReleaseRequest request) {
        couponApplicationService.releaseCoupon(id, request.getOrderNo());
        return Result.success();
    }
}
