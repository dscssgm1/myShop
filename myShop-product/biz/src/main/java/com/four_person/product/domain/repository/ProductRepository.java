package com.four_person.product.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.product.domain.model.Product;
import com.four_person.product.domain.model.ProductCategoryRel;
import com.four_person.product.domain.model.ProductFilter;
import com.four_person.product.domain.model.ProductSku;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    PageResult<Product> page(ProductFilter filter);

    Optional<Product> findById(Long id);

    List<Product> findByIds(List<Long> ids);

    List<Product> findHot(int limit);

    List<Product> findCandidates(List<Long> excludeIds, int limit);

    Product save(Product product);

    void deleteById(Long id);

    boolean deductStock(Long productId, int quantity);

    void restoreStock(Long productId, int quantity);

    // ---- 多类目 ----

    List<Long> findCategoryIds(Long productId);

    void saveCategoryRels(Long productId, List<Long> categoryIds);

    void deleteCategoryRels(Long productId);

    // ---- SKU ----

    List<ProductSku> findSkus(Long productId);

    void saveSkus(Long productId, List<ProductSku> skus);

    void deleteSkus(Long productId);

    boolean deductSkuStock(Long skuId, int quantity);

    void restoreSkuStock(Long skuId, int quantity);
}
