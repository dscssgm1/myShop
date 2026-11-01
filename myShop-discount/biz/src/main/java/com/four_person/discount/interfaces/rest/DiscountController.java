package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.discount.application.DiscountApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountApplicationService discountApplicationService;

    @PostMapping("/calculate")
    public Result<DiscountResultVO> calculate(@Valid @RequestBody DiscountCalculateRequest request) {
        Long userId = request.getUserId() != null ? request.getUserId() : UserContext.getUserId();
        return Result.success(discountApplicationService.calculate(userId, request.getCouponId(), request.getItems()));
    }
}
