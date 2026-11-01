package com.four_person.product.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.product.api.dto.ProductQuery;
import com.four_person.product.api.dto.ProductRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockUpdateRequest;
import com.four_person.product.application.ProductApplicationService;
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

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductApplicationService productApplicationService;

    @GetMapping
    public Result<PageResult<ProductVO>> page(ProductQuery query) {
        return Result.success(productApplicationService.page(query));
    }

    @GetMapping("/hot")
    public Result<List<ProductVO>> hot(@RequestParam(defaultValue = "4") int limit) {
        return Result.success(productApplicationService.hot(limit));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productApplicationService.get(id));
    }

    @PostMapping
    public Result<ProductVO> create(@Valid @RequestBody ProductRequest request) {
        return Result.success(productApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.success(productApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productApplicationService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/stock")
    public Result<Void> restock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        productApplicationService.restock(id, request.getQuantity());
        return Result.success();
    }
}
