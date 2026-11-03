package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.system.api.dto.PointsMallDtos;
import com.four_person.system.application.PointsMallApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分商城：用户端兑换，平台管理端维护商品池。
 */
@RestController
@RequestMapping("/points-mall")
@RequiredArgsConstructor
public class PointsMallController {

    private final PointsMallApplicationService pointsMallApplicationService;

    // ==================== 用户端 ====================

    @GetMapping("/items")
    public Result<List<PointsMallDtos.ItemVO>> items() {
        return Result.success(pointsMallApplicationService.items());
    }

    @PostMapping("/exchange")
    public Result<PointsMallDtos.ExchangeVO> exchange(@Valid @RequestBody PointsMallDtos.ExchangeRequest request) {
        return Result.success("兑换成功，商家将尽快为您发货",
                pointsMallApplicationService.exchange(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/exchanges")
    public Result<List<PointsMallDtos.ExchangeVO>> exchanges() {
        return Result.success(pointsMallApplicationService.exchanges(SecurityUtils.currentUserId()));
    }

    // ==================== 平台管理端 ====================

    @GetMapping("/admin/items")
    public Result<List<PointsMallDtos.ItemVO>> adminList() {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(pointsMallApplicationService.adminList());
    }

    @PostMapping("/admin/items")
    public Result<PointsMallDtos.ItemVO> create(@Valid @RequestBody PointsMallDtos.ItemRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("积分商品已上架", pointsMallApplicationService.create(request));
    }

    @PutMapping("/admin/items/{id}/status")
    public Result<PointsMallDtos.ItemVO> updateStatus(@PathVariable Long id,
                                                      @RequestParam Integer status) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("状态已更新", pointsMallApplicationService.updateStatus(id, status));
    }

    @DeleteMapping("/admin/items/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requireGroup("PLATFORM");
        pointsMallApplicationService.delete(id);
        return Result.success("积分商品已移除", null);
    }
}
