package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.CouponTemplateRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
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

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponApplicationService couponApplicationService;

    /** 领券中心：平台券 + 商家券（登录用户可见已领标记） */
    @GetMapping("/templates")
    public Result<List<CouponTemplateVO>> templates() {
        return Result.success(couponApplicationService.listTemplates(UserContext.getUserIdOrNull()));
    }

    /** 商家自己发放的券 */
    @GetMapping("/templates/mine")
    public Result<List<CouponTemplateVO>> myTemplates() {
        AccessGuard.requirePerm("merchant:marketing");
        return Result.success(couponApplicationService.listByShop(UserContext.requireShopId()));
    }

    /** 发放券模板：平台端发平台券，商家端发商家券 */
    @PostMapping("/templates")
    public Result<CouponTemplateVO> createTemplate(@Valid @RequestBody CouponTemplateRequest request) {
        AccessGuard.requirePerm("admin:coupons:create", "merchant:marketing:coupon-create");
        Long ownerShopId = "MERCHANT".equals(UserContext.getGroup()) ? UserContext.requireShopId() : null;
        return Result.success(couponApplicationService.createTemplate(ownerShopId, request));
    }

    /** 下架券模板：平台下架平台券，商家下架自家券 */
    @PostMapping("/templates/{id}/end")
    public Result<Void> endTemplate(@PathVariable Long id) {
        AccessGuard.requirePerm("admin:coupons:end", "merchant:marketing:coupon-end");
        if ("MERCHANT".equals(UserContext.getGroup())) {
            couponApplicationService.endTemplateForShop(UserContext.requireShopId(), id);
        } else {
            couponApplicationService.endTemplate(id);
        }
        return Result.success();
    }

    @PostMapping("/{templateId}/claim")
    public Result<UserCouponVO> claim(@PathVariable Long templateId) {
        return Result.success(couponApplicationService.claim(UserContext.getUserId(), templateId));
    }

    /** 结算页「本单可领」：按当前商品明细过滤，每张带本单可抵扣金额 */
    @PostMapping("/claimable")
    public Result<List<CouponTemplateVO>> claimable(@Valid @RequestBody DiscountCalculateRequest request) {
        return Result.success(couponApplicationService.claimableForOrder(
                UserContext.getUserIdOrNull(), request.getItems()));
    }

    @GetMapping("/mine")
    public Result<List<UserCouponVO>> mine(@RequestParam(required = false) String status) {
        return Result.success(couponApplicationService.myCoupons(UserContext.getUserId(), status));
    }
}
