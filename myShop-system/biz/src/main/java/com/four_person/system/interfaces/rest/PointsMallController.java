package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.system.api.dto.PointsMallDtos;
import com.four_person.system.application.PointsMallApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分商城：用户花积分兑换平台/商家发布的优惠券。
 */
@RestController
@RequestMapping("/points-mall")
@RequiredArgsConstructor
public class PointsMallController {

    private final PointsMallApplicationService pointsMallApplicationService;

    /** 货架：登录用户可见是否已达限兑标记；匿名也能浏览 */
    @GetMapping("/items")
    public Result<List<PointsMallDtos.CouponVO>> items() {
        return Result.success(pointsMallApplicationService.items(UserContext.getUserIdOrNull()));
    }

    @PostMapping("/exchange")
    public Result<PointsMallDtos.ExchangeVO> exchange(@Valid @RequestBody PointsMallDtos.ExchangeRequest request) {
        return Result.success("兑换成功，优惠券已放入「我的优惠券」",
                pointsMallApplicationService.exchange(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/exchanges")
    public Result<List<PointsMallDtos.ExchangeVO>> exchanges() {
        return Result.success(pointsMallApplicationService.exchanges(SecurityUtils.currentUserId()));
    }
}
