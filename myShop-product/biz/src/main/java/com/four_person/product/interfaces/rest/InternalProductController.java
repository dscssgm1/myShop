package com.four_person.product.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.product.api.dto.CommissionRateRequest;
import com.four_person.product.api.dto.CommissionRateVO;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockChangeRequest;
import com.four_person.product.application.CategoryApplicationService;
import com.four_person.product.application.ProductApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供其他微服务调用的内部接口，不经过网关暴露。
 */
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductApplicationService productApplicationService;
    private final CategoryApplicationService categoryApplicationService;

    /** 订单完成结算佣金时批量取类目费率 */
    @PostMapping("/commission-rates")
    public Result<List<CommissionRateVO>> commissionRates(@Valid @RequestBody CommissionRateRequest request) {
        return Result.success(categoryApplicationService.commissionRates(request.getCategoryIds()));
    }

    @PostMapping("/batch")
    public Result<List<ProductVO>> batch(@Valid @RequestBody ProductBatchRequest request) {
        return Result.success(productApplicationService.batch(request.getIds()));
    }

    @PostMapping("/stock/deduct")
    public Result<Void> deductStock(@Valid @RequestBody StockChangeRequest request) {
        productApplicationService.deductStock(request.getItems());
        return Result.success();
    }

    @PostMapping("/stock/restore")
    public Result<Void> restoreStock(@Valid @RequestBody StockChangeRequest request) {
        productApplicationService.restoreStock(request.getItems());
        return Result.success();
    }

    @GetMapping("/candidates")
    public Result<List<ProductVO>> candidates(
            @RequestParam(required = false) List<Long> excludeIds,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(productApplicationService.candidates(excludeIds, limit));
    }
}
