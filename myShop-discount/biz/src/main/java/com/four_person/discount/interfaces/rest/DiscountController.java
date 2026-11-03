package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.AvailableCoupon;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.discount.application.DiscountApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountApplicationService discountApplicationService;

    /** 订单算价（订单服务内部调用时带 userId；结算页直调时取登录用户） */
    @PostMapping("/calculate")
    public Result<DiscountResultVO> calculate(@Valid @RequestBody DiscountCalculateRequest request) {
        Long userId = request.getUserId() != null ? request.getUserId() : UserContext.getUserId();
        return Result.success(discountApplicationService.calculate(
                userId, request.getCouponIds(), request.getItems()));
    }

    /** 结算页可用优惠券：按当前明细给出每张券实际可抵扣金额 */
    @PostMapping("/available-coupons")
    public Result<List<AvailableCoupon>> availableCoupons(@Valid @RequestBody DiscountCalculateRequest request) {
        return Result.success(discountApplicationService.availableCoupons(
                UserContext.getUserId(), request.getItems()));
    }
}
