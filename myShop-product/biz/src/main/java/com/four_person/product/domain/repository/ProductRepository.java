package com.four_person.product.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.product.domain.model.Product;
import com.four_person.product.domain.model.ProductFilter;

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

    /**
     * 条件扣减库存，库存不足或商品不可售返回 false。
     */
    boolean deductStock(Long productId, int quantity);

    void restoreStock(Long productId, int quantity);
}
