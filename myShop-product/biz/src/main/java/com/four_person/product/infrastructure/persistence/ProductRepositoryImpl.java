package com.four_person.product.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.product.domain.model.Product;
import com.four_person.product.domain.model.ProductCategoryRel;
import com.four_person.product.domain.model.ProductConfigRel;
import com.four_person.product.domain.model.ProductFilter;
import com.four_person.product.domain.model.ProductSku;
import com.four_person.product.domain.repository.ProductRepository;
import com.four_person.product.infrastructure.persistence.mapper.ProductCategoryRelMapper;
import com.four_person.product.infrastructure.persistence.mapper.ProductConfigRelMapper;
import com.four_person.product.infrastructure.persistence.mapper.ProductMapper;
import com.four_person.product.infrastructure.persistence.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductCategoryRelMapper categoryRelMapper;
    private final ProductConfigRelMapper configRelMapper;

    @Override
    public PageResult<Product> page(ProductFilter filter) {
        int pageNo = Math.max(filter.page(), 1);
        int pageSize = filter.size() < 1 ? 8 : Math.min(filter.size(), 100);

        // 买家端默认只看「上架 + 审核通过」；商家/平台管理端（includeAll）不设默认条件，传入时按条件筛选
        Integer status = filter.status();
        String auditStatus = StringUtils.hasText(filter.auditStatus()) ? filter.auditStatus() : null;
        if (!filter.includeAll()) {
            status = status != null ? status : 1;
            auditStatus = auditStatus != null ? auditStatus : "APPROVED";
        }

        LambdaQueryWrapper<Product> wrapper = Wrappers.<Product>lambdaQuery()
                .eq(status != null, Product::getStatus, status)
                .eq(auditStatus != null, Product::getAuditStatus, auditStatus)
                .isNotNull(filter.pendingEdit(), Product::getPendingData)
                .eq(filter.categoryId() != null, Product::getCategoryId, filter.categoryId())
                .eq(filter.brandId() != null, Product::getBrandId, filter.brandId())
                .eq(filter.shopId() != null, Product::getShopId, filter.shopId())
                .ge(filter.minPrice() != null, Product::getPrice, filter.minPrice())
                .le(filter.maxPrice() != null, Product::getPrice, filter.maxPrice())
                .like(StringUtils.hasText(filter.tag()), Product::getTags, filter.tag())
                .notIn(filter.excludeIds() != null && !filter.excludeIds().isEmpty(),
                        Product::getId, filter.excludeIds());

        // 审核中心「待审核」合并视图：新发布待审 + 上架中修改待审核
        if (filter.needsReview()) {
            wrapper.and(w -> w.eq(Product::getAuditStatus, "PENDING")
                    .or().isNotNull(Product::getPendingData));
        }

        if (filter.subCategoryId() != null) {
            List<Long> productIds = categoryRelMapper.selectList(
                            Wrappers.<ProductCategoryRel>lambdaQuery()
                                    .eq(ProductCategoryRel::getCategoryId, filter.subCategoryId()))
                    .stream().map(ProductCategoryRel::getProductId).toList();
            if (productIds.isEmpty()) {
                return PageResult.empty(pageNo, pageSize);
            }
            wrapper.in(Product::getId, productIds);
        }
        if (StringUtils.hasText(filter.keyword())) {
            String keyword = filter.keyword().trim();
            wrapper.and(w -> w.like(Product::getName, keyword).or().like(Product::getShopName, keyword));
        }
        String sort = filter.sort() == null ? "default" : filter.sort();
        switch (sort) {
            case "sales" -> wrapper.orderByDesc(Product::getSales);
            case "priceAsc" -> wrapper.orderByAsc(Product::getPrice);
            case "priceDesc" -> wrapper.orderByDesc(Product::getPrice);
            case "random" -> wrapper.last("ORDER BY RAND()");
            default -> wrapper.orderByAsc(Product::getId);
        }
        // random 场景前端只关心本批内容，跳过 count 查询（同时避免 ORDER BY RAND() 进入 count SQL）
        Page<Product> page = productMapper.selectPage(
                new Page<>(pageNo, pageSize, !"random".equals(sort)), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public long countPendingAudit() {
        // 待审核 = 新发布待审 + 已通过但有待审核修改
        return productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getAuditStatus, "PENDING")
                .or()
                .isNotNull(Product::getPendingData));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(productMapper.selectById(id));
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return productMapper.selectBatchIds(ids);
    }

    @Override
    public List<Product> findHot(int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        Page<Product> page = productMapper.selectPage(new Page<>(1, size, false),
                Wrappers.<Product>lambdaQuery()
                        .eq(Product::getStatus, 1)
                        .eq(Product::getAuditStatus, "APPROVED")
                        .orderByDesc(Product::getSales)
                        .orderByAsc(Product::getId));
        return page.getRecords();
    }

    @Override
    public List<Product> findCandidates(List<Long> excludeIds, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        Page<Product> page = productMapper.selectPage(new Page<>(1, size, false),
                Wrappers.<Product>lambdaQuery()
                        .eq(Product::getStatus, 1)
                        .eq(Product::getAuditStatus, "APPROVED")
                        .notIn(excludeIds != null && !excludeIds.isEmpty(), Product::getId, excludeIds)
                        .orderByDesc(Product::getSales)
                        .orderByAsc(Product::getId));
        return page.getRecords();
    }

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
        return product;
    }

    @Override
    public void clearPendingEdit(Long id) {
        productMapper.update(null, Wrappers.<Product>update()
                .set("pending_data", null)
                .eq("id", id));
    }

    @Override
    public void deleteById(Long id) {
        productMapper.deleteById(id);
    }

    @Override
    public boolean deductStock(Long productId, int quantity) {
        return productMapper.deductStock(productId, quantity) > 0;
    }

    @Override
    public void restoreStock(Long productId, int quantity) {
        productMapper.restoreStock(productId, quantity);
    }

    // ---- 多类目 ----

    @Override
    public List<Long> findCategoryIds(Long productId) {
        return categoryRelMapper.selectList(Wrappers.<ProductCategoryRel>lambdaQuery()
                        .eq(ProductCategoryRel::getProductId, productId))
                .stream().map(ProductCategoryRel::getCategoryId).toList();
    }

    @Override
    public void saveCategoryRels(Long productId, List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            ProductCategoryRel rel = new ProductCategoryRel();
            rel.setProductId(productId);
            rel.setCategoryId(categoryId);
            categoryRelMapper.insert(rel);
        }
    }

    @Override
    public void deleteCategoryRels(Long productId) {
        categoryRelMapper.delete(Wrappers.<ProductCategoryRel>lambdaQuery()
                .eq(ProductCategoryRel::getProductId, productId));
    }

    // ---- 服务保障（配置中心 key） ----

    @Override
    public List<String> findServiceKeys(Long productId) {
        return configRelMapper.selectList(Wrappers.<ProductConfigRel>lambdaQuery()
                        .eq(ProductConfigRel::getProductId, productId)
                        .orderByAsc(ProductConfigRel::getId))
                .stream().map(ProductConfigRel::getConfigKey).toList();
    }

    @Override
    public void saveServiceRels(Long productId, List<String> configKeys) {
        for (String configKey : configKeys) {
            ProductConfigRel rel = new ProductConfigRel();
            rel.setProductId(productId);
            rel.setConfigKey(configKey);
            configRelMapper.insert(rel);
        }
    }

    @Override
    public void deleteServiceRels(Long productId) {
        configRelMapper.delete(Wrappers.<ProductConfigRel>lambdaQuery()
                .eq(ProductConfigRel::getProductId, productId));
    }

    // ---- SKU ----

    @Override
    public List<ProductSku> findSkus(Long productId) {
        return productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    @Override
    public void saveSkus(Long productId, List<ProductSku> skus) {
        for (ProductSku sku : skus) {
            sku.setProductId(productId);
            productSkuMapper.insert(sku);
        }
    }

    @Override
    public void deleteSkus(Long productId) {
        productSkuMapper.delete(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId));
    }

    @Override
    public boolean deductSkuStock(Long skuId, int quantity) {
        return productSkuMapper.deductStock(skuId, quantity) > 0;
    }

    @Override
    public void restoreSkuStock(Long skuId, int quantity) {
        productSkuMapper.restoreStock(skuId, quantity);
    }
}
