package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.PromotionRequest;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.application.PromotionApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionApplicationService promotionApplicationService;

    /** 全部活动（前台首页/商品角标公开读取，见网关白名单） */
    @GetMapping
    public Result<List<PromotionVO>> list(@RequestParam(defaultValue = "active") String status) {
        return Result.success(promotionApplicationService.list(status));
    }

    /** 商家自建活动列表 */
    @GetMapping("/merchant")
    public Result<List<PromotionVO>> merchantList() {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(promotionApplicationService.listByShop(UserContext.requireShopId()));
    }

    @PostMapping("/merchant")
    public Result<PromotionVO> merchantCreate(@Valid @RequestBody PromotionRequest request) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(promotionApplicationService.createForShop(UserContext.requireShopId(), request));
    }

    @PutMapping("/merchant/{id}")
    public Result<PromotionVO> merchantUpdate(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(promotionApplicationService.updateForShop(UserContext.requireShopId(), id, request));
    }

    @PostMapping("/merchant/{id}/end")
    public Result<Void> merchantEnd(@PathVariable Long id) {
        AccessGuard.requireGroup("MERCHANT");
        promotionApplicationService.endForShop(UserContext.requireShopId(), id);
        return Result.success();
    }

    @PostMapping
    public Result<PromotionVO> create(@Valid @RequestBody PromotionRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(promotionApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<PromotionVO> update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(promotionApplicationService.update(id, request));
    }

    @PostMapping("/{id}/end")
    public Result<Void> end(@PathVariable Long id) {
        AccessGuard.requireGroup("PLATFORM");
        promotionApplicationService.end(id);
        return Result.success();
    }
}
