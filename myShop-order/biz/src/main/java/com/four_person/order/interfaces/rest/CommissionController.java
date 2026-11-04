package com.four_person.order.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.order.api.dto.CommissionDtos;
import com.four_person.order.application.CommissionApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品佣金账单：商家看自己的，平台看全平台。
 * 挂在 /orders 下，网关 /api/orders/** 路由已覆盖，无需新增网关配置。
 */
@RestController
@RequestMapping("/orders/commission")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionApplicationService commissionApplicationService;

    /** 商家账单汇总 */
    @GetMapping("/merchant/summary")
    public Result<CommissionDtos.CommissionSummaryVO> merchantSummary() {
        AccessGuard.requirePerm("merchant:bill");
        return Result.success(commissionApplicationService.summary(UserContext.requireShopId()));
    }

    /** 商家佣金流水 */
    @GetMapping("/merchant/page")
    public Result<PageResult<CommissionDtos.CommissionVO>> merchantPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requirePerm("merchant:bill");
        return Result.success(commissionApplicationService.page(UserContext.requireShopId(), page, size));
    }

    /** 平台账单汇总：全平台累计 + 各店铺 */
    @GetMapping("/admin/summary")
    public Result<CommissionDtos.CommissionAdminSummaryVO> adminSummary() {
        AccessGuard.requirePerm("admin:commission");
        return Result.success(commissionApplicationService.adminSummary());
    }

    /** 平台佣金流水；shopId 为空时看全平台 */
    @GetMapping("/admin/page")
    public Result<PageResult<CommissionDtos.CommissionVO>> adminPage(
            @RequestParam(required = false) Long shopId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requirePerm("admin:commission");
        return Result.success(commissionApplicationService.page(shopId, page, size));
    }
}
