package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.discount.api.dto.CouponClaimRequest;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.UserCouponVO;
import com.four_person.discount.application.CouponApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供订单服务调用的优惠券核销/释放接口，供系统服务调用的积分商城兑换接口。
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

    // ==================== 积分商城：积分兑换优惠券 ====================

    /** 积分兑换券货架（system 服务积分商城列表用） */
    @GetMapping("/points-mall")
    public Result<List<CouponTemplateVO>> pointsMall(@RequestParam(required = false) Long userId) {
        return Result.success(couponApplicationService.pointsMallItems(userId));
    }

    /** 代用户领取一张券（system 服务扣积分成功后调用，占库存、校验限领） */
    @PostMapping("/claim-for-user")
    public Result<UserCouponVO> claimForUser(@Valid @RequestBody CouponClaimRequest request) {
        return Result.success(couponApplicationService.claimForUser(request.getUserId(), request.getTemplateId()));
    }
}
