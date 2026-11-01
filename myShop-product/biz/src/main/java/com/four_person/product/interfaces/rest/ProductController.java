package com.four_person.product.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
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

import java.math.BigDecimal;
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

    /** 商家：发布商品（提交审核） */
    @PostMapping
    public Result<ProductVO> create(@Valid @RequestBody ProductRequest request) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        return Result.success("商品已提交，等待审核", productApplicationService.create(request));
    }

    /** 商家/平台：修改商品 */
    @PutMapping("/{id}")
    public Result<ProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        return Result.success("商品已更新", productApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        productApplicationService.delete(id);
        return Result.success();
    }

    /** 补货 */
    @PutMapping("/{id}/stock")
    public Result<Void> restock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        productApplicationService.restock(id, request.getQuantity());
        return Result.success();
    }

    /** 设置促销价 */
    @PutMapping("/{id}/discount")
    public Result<ProductVO> setDiscount(@PathVariable Long id, @RequestParam BigDecimal discountPrice) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        return Result.success("折扣已设置", productApplicationService.setDiscount(id, discountPrice));
    }

    /** 上架 */
    @PutMapping("/{id}/on-sale")
    public Result<Void> onSale(@PathVariable Long id) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        productApplicationService.onSale(id);
        return Result.success("已上架", null);
    }

    /** 下架（需说明原因） */
    @PutMapping("/{id}/off-sale")
    public Result<Void> offSale(@PathVariable Long id, @RequestParam(required = false) String reason) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        productApplicationService.offSale(id, reason);
        return Result.success("已下架", null);
    }

    /** 平台审核 */
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam boolean approved) {
        AccessGuard.requireGroup("PLATFORM");
        productApplicationService.audit(id, approved);
        return Result.success(approved ? "审核已通过" : "审核已驳回", null);
    }
}
