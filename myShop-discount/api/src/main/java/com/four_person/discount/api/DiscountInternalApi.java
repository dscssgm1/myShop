package com.four_person.discount.api;

import com.four_person.common.api.Result;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 折扣服务内部契约，供订单服务算价与核销优惠券。
 */
@FeignClient(name = "myshop-discount", url = "${myshop.clients.discount-url:}")
public interface DiscountInternalApi {

    @PostMapping("/discounts/calculate")
    Result<DiscountResultVO> calculate(@RequestBody DiscountCalculateRequest request);

    @PostMapping("/internal/coupons/{id}/use")
    Result<Void> useCoupon(@PathVariable("id") Long couponId, @RequestBody CouponUseRequest request);

    @PostMapping("/internal/coupons/{id}/release")
    Result<Void> releaseCoupon(@PathVariable("id") Long couponId, @RequestBody CouponReleaseRequest request);
}
