package com.four_person.product.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.product.domain.model.ProductCategory;
import com.four_person.product.infrastructure.persistence.mapper.ProductCategoryMapper;
import com.four_person.product.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final ProductCategoryMapper categoryMapper;

    @Override
    public List<ProductCategory> findAll() {
        return categoryMapper.selectList(Wrappers.<ProductCategory>lambdaQuery()
                .orderByAsc(ProductCategory::getSort, ProductCategory::getId));
    }

    @Override
    public Optional<ProductCategory> findById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id));
    }
}
