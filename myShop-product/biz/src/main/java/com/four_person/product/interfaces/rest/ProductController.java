package com.four_person.product.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.product.api.dto.ProductQuery;
import com.four_person.product.api.dto.ProductRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockUpdateRequest;
import com.four_person.product.application.ProductApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
        // 上架/审核状态筛选属于管理端能力，带这些参数时须有商品管理菜单权限
        if (query.getStatus() != null || StringUtils.hasText(query.getAuditStatus())
                || Boolean.TRUE.equals(query.getPendingEdit())
                || Boolean.TRUE.equals(query.getNeedsReview())) {
            AccessGuard.requirePerm("merchant:products", "admin:products");
        }
        // 商家管理端：包含未上架/未审核商品，商家只能查本店
        if (Boolean.TRUE.equals(query.getIncludeAll())) {
            AccessGuard.requirePerm("merchant:products", "admin:products");
            if ("MERCHANT".equals(UserContext.getGroup())) {
                query.setShopId(UserContext.requireShopId());
            }
        }
        return Result.success(productApplicationService.page(query));
    }

    @GetMapping("/hot")
    public Result<List<ProductVO>> hot(@RequestParam(defaultValue = "4") int limit) {
        return Result.success(productApplicationService.hot(limit));
    }

    /** 平台侧角标：待审核商品数（新发布待审 + 修改待审核），菜单「商品审核」红点用 */
    @GetMapping("/pending-audit-count")
    public Result<Long> pendingAuditCount() {
        AccessGuard.requirePerm("admin:audit");
        return Result.success(productApplicationService.countPendingAudit());
    }

    /** 公开：按 id 批量查询（收藏、浏览足迹展示用） */
    @GetMapping("/batch")
    public Result<List<ProductVO>> batch(@RequestParam String ids) {
        List<Long> idList = java.util.Arrays.stream(ids.split(","))
                .map(String::trim).filter(StringUtils::hasText)
                .map(Long::valueOf).distinct().toList();
        return Result.success(idList.isEmpty() ? List.of() : productApplicationService.batch(idList));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productApplicationService.get(id));
    }

    /** 商家：发布商品（提交审核） */
    @PostMapping
    public Result<ProductVO> create(@Valid @RequestBody ProductRequest request) {
        AccessGuard.requirePerm("merchant:products:create", "admin:products:create");
        return Result.success("商品已提交，等待审核", productApplicationService.create(request));
    }

    /** 商家/平台：修改商品 */
    @PutMapping("/{id}")
    public Result<ProductVO> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        AccessGuard.requirePerm("merchant:products:edit", "admin:products:edit");
        return Result.success("商品已更新", productApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requirePerm("merchant:products:delete", "admin:products:delete");
        productApplicationService.delete(id);
        return Result.success();
    }

    /** 补货 */
    @PutMapping("/{id}/stock")
    public Result<Void> restock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        AccessGuard.requirePerm("merchant:products:edit", "admin:products:edit");
        productApplicationService.restock(id, request.getQuantity());
        return Result.success();
    }

    /** 修改售价（直接生效，无需审核） */
    @PutMapping("/{id}/price")
    public Result<ProductVO> setPrice(@PathVariable Long id, @RequestParam BigDecimal price) {
        AccessGuard.requirePerm("merchant:products:price", "admin:products:edit");
        return Result.success("售价已更新", productApplicationService.setPrice(id, price));
    }

    /** 上架 */
    @PutMapping("/{id}/on-sale")
    public Result<Void> onSale(@PathVariable Long id) {
        AccessGuard.requirePerm("merchant:products:status", "admin:products:status");
        productApplicationService.onSale(id);
        return Result.success("已上架", null);
    }

    /** 下架（需说明原因） */
    @PutMapping("/{id}/off-sale")
    public Result<Void> offSale(@PathVariable Long id, @RequestParam(required = false) String reason) {
        AccessGuard.requirePerm("merchant:products:status", "admin:products:status");
        productApplicationService.offSale(id, reason);
        return Result.success("已下架", null);
    }

    /** 平台审核：通过即自动上架；驳回必须填写原因，商家据原因修改后可重新提交 */
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id,
                              @RequestParam boolean approved,
                              @RequestParam(required = false) String reason) {
        AccessGuard.requirePerm("admin:audit:product");
        productApplicationService.audit(id, approved, reason);
        return Result.success(approved ? "审核已通过，商品已上架" : "已驳回", null);
    }
}
