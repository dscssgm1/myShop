package com.four_person.product.domain.repository;

import com.four_person.product.domain.model.ProductCategory;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    List<ProductCategory> findAll();

    List<ProductCategory> findByParentId(Long parentId);

    List<ProductCategory> findByLevel(Integer level);

    Optional<ProductCategory> findById(Long id);

    boolean existsByName(String name, Long parentId);

    ProductCategory save(ProductCategory category);

    void deleteById(Long id);
}
