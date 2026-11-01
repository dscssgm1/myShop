package com.four_person.product.domain.repository;

import com.four_person.product.domain.model.ProductCategory;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    List<ProductCategory> findAll();

    Optional<ProductCategory> findById(Long id);
}
