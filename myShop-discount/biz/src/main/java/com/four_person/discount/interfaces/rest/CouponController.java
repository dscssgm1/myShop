package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.CouponTemplateRequest;
import com.four_person.discount.api.dto.CouponTemplateVO;
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

    @GetMapping("/templates")
    public Result<List<CouponTemplateVO>> templates() {
        return Result.success(couponApplicationService.listTemplates(UserContext.getUserIdOrNull()));
    }

    @PostMapping("/templates")
    public Result<CouponTemplateVO> createTemplate(@Valid @RequestBody CouponTemplateRequest request) {
        return Result.success(couponApplicationService.createTemplate(request));
    }

    @PostMapping("/{templateId}/claim")
    public Result<UserCouponVO> claim(@PathVariable Long templateId) {
        return Result.success(couponApplicationService.claim(UserContext.getUserId(), templateId));
    }

    @GetMapping("/mine")
    public Result<List<UserCouponVO>> mine(@RequestParam(required = false) String status) {
        return Result.success(couponApplicationService.myCoupons(UserContext.getUserId(), status));
    }
}
