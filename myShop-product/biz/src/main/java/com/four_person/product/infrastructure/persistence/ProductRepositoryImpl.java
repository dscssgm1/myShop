package com.four_person.product.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.product.domain.model.Product;
import com.four_person.product.domain.model.ProductCategoryRel;
import com.four_person.product.domain.model.ProductFilter;
import com.four_person.product.domain.model.ProductSku;
import com.four_person.product.domain.repository.ProductRepository;
import com.four_person.product.infrastructure.persistence.mapper.ProductCategoryRelMapper;
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

    @Override
    public PageResult<Product> page(ProductFilter filter) {
        int pageNo = Math.max(filter.page(), 1);
        int pageSize = filter.size() < 1 ? 8 : Math.min(filter.size(), 100);

        LambdaQueryWrapper<Product> wrapper = Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1)
                .eq(Product::getAuditStatus, "APPROVED")
                .eq(filter.categoryId() != null, Product::getCategoryId, filter.categoryId())
                .eq(filter.brandId() != null, Product::getBrandId, filter.brandId())
                .eq(filter.shopId() != null, Product::getShopId, filter.shopId())
                .ge(filter.minPrice() != null, Product::getPrice, filter.minPrice())
                .le(filter.maxPrice() != null, Product::getPrice, filter.maxPrice())
                .like(StringUtils.hasText(filter.tag()), Product::getTags, filter.tag());

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
        switch (filter.sort() == null ? "default" : filter.sort()) {
            case "sales" -> wrapper.orderByDesc(Product::getSales);
            case "priceAsc" -> wrapper.orderByAsc(Product::getPrice);
            case "priceDesc" -> wrapper.orderByDesc(Product::getPrice);
            default -> wrapper.orderByAsc(Product::getId);
        }
        Page<Product> page = productMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
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
