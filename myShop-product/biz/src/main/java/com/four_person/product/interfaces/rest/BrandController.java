package com.four_person.product.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.product.api.dto.BrandDtos;
import com.four_person.product.application.BrandApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandApplicationService brandApplicationService;

    @GetMapping
    public Result<List<BrandDtos.BrandVO>> list() {
        return Result.success(brandApplicationService.list());
    }

    @PostMapping
    public Result<BrandDtos.BrandVO> create(@Valid @RequestBody BrandDtos.BrandRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("品牌已新增", brandApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<BrandDtos.BrandVO> update(@PathVariable Long id, @Valid @RequestBody BrandDtos.BrandRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("品牌已更新", brandApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requireGroup("PLATFORM");
        brandApplicationService.delete(id);
        return Result.success("品牌已删除", null);
    }
}
