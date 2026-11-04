package com.four_person.product.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.product.api.dto.SpecDtos;
import com.four_person.product.application.MerchantSpecApplicationService;
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

/** 商家规格库：规格与规格值由商家自行维护，按店铺隔离 */
@RestController
@RequestMapping("/specs/merchant")
@RequiredArgsConstructor
public class MerchantSpecController {

    private final MerchantSpecApplicationService merchantSpecApplicationService;

    @GetMapping
    public Result<List<SpecDtos.SpecTemplateVO>> list() {
        AccessGuard.requirePerm("merchant:specs", "merchant:specs:create");
        return Result.success(merchantSpecApplicationService.list(UserContext.requireShopId()));
    }

    @PostMapping
    public Result<SpecDtos.SpecTemplateVO> create(@Valid @RequestBody SpecDtos.SpecTemplateRequest request) {
        AccessGuard.requirePerm("merchant:specs:create");
        return Result.success("规格已新增", merchantSpecApplicationService.create(UserContext.requireShopId(), request));
    }

    @PutMapping("/{id}")
    public Result<SpecDtos.SpecTemplateVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody SpecDtos.SpecTemplateRequest request) {
        AccessGuard.requirePerm("merchant:specs:create");
        return Result.success("规格已更新", merchantSpecApplicationService.update(UserContext.requireShopId(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requirePerm("merchant:specs:delete");
        merchantSpecApplicationService.delete(UserContext.requireShopId(), id);
        return Result.success("规格已删除", null);
    }
}
