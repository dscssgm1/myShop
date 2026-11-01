package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.AssetDtos;
import com.four_person.system.application.FootprintApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/footprints")
@RequiredArgsConstructor
public class FootprintController {

    private final FootprintApplicationService footprintApplicationService;

    @GetMapping
    public Result<List<AssetDtos.FootprintVO>> list(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(footprintApplicationService.list(SecurityUtils.currentUserId(), limit));
    }

    @PostMapping
    public Result<Void> record(@Valid @RequestBody AssetDtos.ProductRefRequest request) {
        footprintApplicationService.record(SecurityUtils.currentUserId(), request.getProductId());
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> clear() {
        footprintApplicationService.clear(SecurityUtils.currentUserId());
        return Result.success("足迹已清空", null);
    }
}
