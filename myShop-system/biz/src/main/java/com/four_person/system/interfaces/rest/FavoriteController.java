package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.AssetDtos;
import com.four_person.system.application.FavoriteApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteApplicationService favoriteApplicationService;

    @GetMapping
    public Result<List<AssetDtos.FavoriteVO>> list() {
        return Result.success(favoriteApplicationService.list(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{productId}/exists")
    public Result<Boolean> exists(@PathVariable Long productId) {
        return Result.success(favoriteApplicationService.exists(SecurityUtils.currentUserId(), productId));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody AssetDtos.ProductRefRequest request) {
        favoriteApplicationService.add(SecurityUtils.currentUserId(), request.getProductId());
        return Result.success("已收藏", null);
    }

    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        favoriteApplicationService.remove(SecurityUtils.currentUserId(), productId);
        return Result.success("已取消收藏", null);
    }
}
