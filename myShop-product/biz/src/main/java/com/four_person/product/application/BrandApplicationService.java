package com.four_person.product.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.product.api.dto.BrandDtos;
import com.four_person.product.domain.model.Brand;
import com.four_person.product.enums.ProductErrorCode;
import com.four_person.product.infrastructure.persistence.mapper.BrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandApplicationService {

    private final BrandMapper brandMapper;

    public List<BrandDtos.BrandVO> list() {
        return brandMapper.selectList(Wrappers.<Brand>lambdaQuery()
                        .orderByAsc(Brand::getSort).orderByAsc(Brand::getId))
                .stream().map(this::toVO).toList();
    }

    public BrandDtos.BrandVO create(BrandDtos.BrandRequest request) {
        Brand brand = new Brand();
        brand.setName(request.getName());
        brand.setLogo(request.getLogo());
        brand.setSort(request.getSort() == null ? 0 : request.getSort());
        brandMapper.insert(brand);
        return toVO(brand);
    }

    public BrandDtos.BrandVO update(Long id, BrandDtos.BrandRequest request) {
        Brand brand = require(id);
        brand.setName(request.getName());
        brand.setLogo(request.getLogo());
        brand.setSort(request.getSort() == null ? 0 : request.getSort());
        brandMapper.updateById(brand);
        return toVO(brand);
    }

    public void delete(Long id) {
        require(id);
        brandMapper.deleteById(id);
    }

    private Brand require(Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException(ProductErrorCode.BRAND_NOT_FOUND);
        }
        return brand;
    }

    private BrandDtos.BrandVO toVO(Brand brand) {
        BrandDtos.BrandVO vo = new BrandDtos.BrandVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setLogo(brand.getLogo());
        vo.setSort(brand.getSort());
        return vo;
    }
}
