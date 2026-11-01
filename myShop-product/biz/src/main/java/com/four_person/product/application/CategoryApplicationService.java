package com.four_person.product.application;

import com.four_person.product.api.dto.CategoryVO;
import com.four_person.product.domain.model.ProductCategory;
import com.four_person.product.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryApplicationService {

    private final CategoryRepository categoryRepository;

    public List<CategoryVO> listAll() {
        return categoryRepository.findAll().stream().map(this::toVO).toList();
    }

    private CategoryVO toVO(ProductCategory category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setColor(category.getColor());
        vo.setSort(category.getSort());
        return vo;
    }
}
