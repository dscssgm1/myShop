package com.four_person.discount.api;

import com.four_person.common.api.Result;
import com.four_person.discount.api.dto.CouponClaimRequest;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.discount.api.dto.UserCouponVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 折扣服务内部契约，供订单服务算价、核销优惠券，供系统服务积分商城兑换券。
 */
@FeignClient(name = "myshop-discount", url = "${myshop.clients.discount-url:}")
public interface DiscountInternalApi {

    @PostMapping("/discounts/calculate")
    Result<DiscountResultVO> calculate(@RequestBody DiscountCalculateRequest request);

    @PostMapping("/internal/coupons/{id}/use")
    Result<Void> useCoupon(@PathVariable("id") Long couponId, @RequestBody CouponUseRequest request);

    @PostMapping("/internal/coupons/{id}/release")
    Result<Void> releaseCoupon(@PathVariable("id") Long couponId, @RequestBody CouponReleaseRequest request);

    // ==================== 积分商城：积分兑换优惠券 ====================

    /** 积分兑换券货架：设了积分价（points_price > 0）且在有效期内的券模板（userId 用于标记限兑） */
    @GetMapping("/internal/coupons/points-mall")
    Result<List<CouponTemplateVO>> pointsMallTemplates(@RequestParam("userId") Long userId);

    /** 代用户领取一张券（占库存、校验限领），system 服务扣积分成功后调用 */
    @PostMapping("/internal/coupons/claim-for-user")
    Result<UserCouponVO> claimForUser(@RequestBody CouponClaimRequest request);
}
