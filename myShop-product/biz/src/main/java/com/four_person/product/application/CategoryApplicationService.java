package com.four_person.product.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.product.api.dto.CategoryRequest;
import com.four_person.product.api.dto.CategoryVO;
import com.four_person.product.api.dto.CommissionRateVO;
import com.four_person.product.domain.model.ProductCategory;
import com.four_person.product.domain.repository.CategoryRepository;
import com.four_person.product.enums.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryApplicationService {

    private final CategoryRepository categoryRepository;

    /** 大类列表（含小类 children） */
    public List<CategoryVO> tree() {
        List<ProductCategory> majors = categoryRepository.findByLevel(1);
        return majors.stream().map(major -> {
            CategoryVO vo = toVO(major);
            List<CategoryVO> children = categoryRepository.findByParentId(major.getId()).stream()
                    .map(this::toVO)
                    .toList();
            vo.setChildren(children);
            return vo;
        }).toList();
    }

    /** 平铺所有分类（兼容旧接口） */
    public List<CategoryVO> listAll() {
        List<CategoryVO> list = new ArrayList<>();
        categoryRepository.findAll().forEach(category -> list.add(toVO(category)));
        return list;
    }

    /**
     * 类目生效佣金费率：小类未单独设置时继承大类，都未设置按 0（不抽佣）。
     * 查不到的分类按 0 处理，不因个别脏数据影响整单结算。
     */
    public List<CommissionRateVO> commissionRates(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductCategory> all = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity(), (a, b) -> a));
        return new LinkedHashSet<>(categoryIds).stream()
                .filter(id -> id != null)
                .map(id -> {
                    ProductCategory category = all.get(id);
                    return new CommissionRateVO(id,
                            category == null ? "" : category.getName(),
                            effectiveRate(id, all));
                })
                .toList();
    }

    public CategoryVO create(CategoryRequest request) {
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        Integer level = parentId == 0 ? 1 : 2;
        if (level == 2) {
            categoryRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        }
        if (categoryRepository.existsByName(request.getName(), parentId)) {
            throw new BusinessException(40901, "同级分类下名称已存在");
        }
        ProductCategory category = new ProductCategory();
        category.setParentId(parentId);
        category.setLevel(level);
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setCommissionRate(request.getCommissionRate());
        categoryRepository.save(category);
        return toVO(category);
    }

    public CategoryVO update(Long id, CategoryRequest request) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        category.setName(request.getName());
        if (request.getIcon() != null) {
            category.setIcon(request.getIcon());
        }
        if (request.getColor() != null) {
            category.setColor(request.getColor());
        }
        if (request.getSort() != null) {
            category.setSort(request.getSort());
        }
        // 小类传 null 即清空费率、改为继承大类；大类传 null 即不抽佣
        category.setCommissionRate(request.getCommissionRate());
        categoryRepository.save(category);
        return toVO(category);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        // 删除大类时一并删除其下小类
        categoryRepository.findByParentId(id).forEach(child -> categoryRepository.deleteById(child.getId()));
        categoryRepository.deleteById(id);
    }

    private BigDecimal effectiveRate(Long categoryId, Map<Long, ProductCategory> all) {
        ProductCategory category = all.get(categoryId);
        if (category == null) {
            return BigDecimal.ZERO;
        }
        if (category.getCommissionRate() != null) {
            return category.getCommissionRate();
        }
        ProductCategory parent = category.getParentId() == null ? null : all.get(category.getParentId());
        if (parent != null && parent.getCommissionRate() != null) {
            return parent.getCommissionRate();
        }
        return BigDecimal.ZERO;
    }

    private CategoryVO toVO(ProductCategory category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setLevel(category.getLevel());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setColor(category.getColor());
        vo.setSort(category.getSort());
        vo.setCommissionRate(category.getCommissionRate());
        if (category.getCommissionRate() != null) {
            vo.setEffectiveCommissionRate(category.getCommissionRate());
        } else if (category.getParentId() != null && category.getParentId() != 0) {
            // 小类未单独设置：回填大类的费率，前端可直接展示"继承中的实际值"
            vo.setEffectiveCommissionRate(categoryRepository.findById(category.getParentId())
                    .map(ProductCategory::getCommissionRate)
                    .orElse(BigDecimal.ZERO));
        } else {
            vo.setEffectiveCommissionRate(BigDecimal.ZERO);
        }
        return vo;
    }
}
