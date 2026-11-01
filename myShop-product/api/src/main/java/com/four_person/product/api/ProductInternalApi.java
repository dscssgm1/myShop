package com.four_person.product.api;

import com.four_person.common.api.Result;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockChangeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务内部契约，供订单、折扣等服务调用。
 */
@FeignClient(name = "myshop-product", url = "${myshop.clients.product-url:}")
public interface ProductInternalApi {

    @PostMapping("/internal/products/batch")
    Result<List<ProductVO>> batch(@RequestBody ProductBatchRequest request);

    @PostMapping("/internal/products/stock/deduct")
    Result<Void> deductStock(@RequestBody StockChangeRequest request);

    @PostMapping("/internal/products/stock/restore")
    Result<Void> restoreStock(@RequestBody StockChangeRequest request);

    @GetMapping("/internal/products/candidates")
    Result<List<ProductVO>> candidates(@RequestParam(value = "excludeIds", required = false) List<Long> excludeIds,
                                       @RequestParam("limit") int limit);
}
