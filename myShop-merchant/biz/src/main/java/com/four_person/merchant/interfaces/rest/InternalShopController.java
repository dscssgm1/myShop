package com.four_person.merchant.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.merchant.application.ShopApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/shops")
@RequiredArgsConstructor
public class InternalShopController {

    private final ShopApplicationService shopApplicationService;

    @GetMapping("/{id}")
    public Result<MerchantDtos.ShopVO> shop(@PathVariable Long id) {
        return Result.success(shopApplicationService.internalGet(id));
    }
}
