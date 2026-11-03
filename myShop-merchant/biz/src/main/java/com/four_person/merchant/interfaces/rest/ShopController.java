package com.four_person.merchant.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.merchant.application.ShopApplicationService;
import jakarta.validation.Valid;

import java.math.BigDecimal;
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
@RequiredArgsConstructor
public class ShopController {

    private final ShopApplicationService shopApplicationService;

    /** 买家端：店铺列表 */
    @GetMapping("/shops")
    public Result<PageResult<MerchantDtos.ShopVO>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String category,
                                                        @RequestParam(required = false) BigDecimal minRating,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.success(shopApplicationService.page(keyword, category, minRating, page, size));
    }

    /** 买家端：店铺详情 */
    @GetMapping("/shops/{id}")
    public Result<MerchantDtos.ShopVO> detail(@PathVariable Long id) {
        return Result.success(shopApplicationService.detail(id));
    }

    // ---------------- 商家中心 ----------------

    @GetMapping("/merchant/shop")
    public Result<MerchantDtos.ShopVO> myShop() {
        return Result.success(shopApplicationService.myShop());
    }

    @PutMapping("/merchant/shop")
    public Result<MerchantDtos.ShopVO> updateMyShop(@Valid @RequestBody MerchantDtos.ShopRequest request) {
        return Result.success("店铺信息已保存", shopApplicationService.updateMy(request));
    }

    /** 买家申请开店 */
    @PostMapping("/merchant/apply")
    public Result<MerchantDtos.ApplyVO> apply(@Valid @RequestBody MerchantDtos.ApplyRequest request) {
        return Result.success("入驻申请已提交", shopApplicationService.apply(request));
    }

    @GetMapping("/merchant/apply/mine")
    public Result<List<MerchantDtos.ApplyVO>> myApplies() {
        return Result.success(shopApplicationService.myApplies());
    }
}
