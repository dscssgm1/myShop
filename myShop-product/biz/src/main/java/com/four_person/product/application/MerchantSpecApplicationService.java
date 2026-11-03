package com.four_person.product.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.exception.BusinessException;
import com.four_person.product.api.dto.SpecDtos;
import com.four_person.product.domain.model.MerchantSpec;
import com.four_person.product.enums.ProductErrorCode;
import com.four_person.product.infrastructure.persistence.mapper.MerchantSpecMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 商家规格库：规格与规格值由商家自行维护，商品编辑时从这里选择 */
@Service
@RequiredArgsConstructor
public class MerchantSpecApplicationService {

    private final MerchantSpecMapper merchantSpecMapper;
    private final ObjectMapper objectMapper;

    public List<SpecDtos.SpecTemplateVO> list(Long shopId) {
        return merchantSpecMapper.selectList(Wrappers.<MerchantSpec>lambdaQuery()
                        .eq(MerchantSpec::getShopId, shopId)
                        .orderByAsc(MerchantSpec::getSort).orderByDesc(MerchantSpec::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public SpecDtos.SpecTemplateVO create(Long shopId, SpecDtos.SpecTemplateRequest request) {
        requireUniqueName(shopId, request.getName(), null);
        MerchantSpec spec = new MerchantSpec();
        spec.setShopId(shopId);
        spec.setName(request.getName().trim());
        spec.setOptions(writeOptions(request.getOptions()));
        spec.setSort(request.getSort() == null ? 0 : request.getSort());
        merchantSpecMapper.insert(spec);
        return toVO(spec);
    }

    @Transactional
    public SpecDtos.SpecTemplateVO update(Long shopId, Long id, SpecDtos.SpecTemplateRequest request) {
        MerchantSpec spec = require(shopId, id);
        requireUniqueName(shopId, request.getName(), id);
        spec.setName(request.getName().trim());
        spec.setOptions(writeOptions(request.getOptions()));
        if (request.getSort() != null) {
            spec.setSort(request.getSort());
        }
        merchantSpecMapper.updateById(spec);
        return toVO(spec);
    }

    @Transactional
    public void delete(Long shopId, Long id) {
        require(shopId, id);
        merchantSpecMapper.deleteById(id);
    }

    // ---------------- 私有方法 ----------------

    private MerchantSpec require(Long shopId, Long id) {
        MerchantSpec spec = merchantSpecMapper.selectById(id);
        // 只能操作自己店铺的规格
        if (spec == null || !spec.getShopId().equals(shopId)) {
            throw new BusinessException(ProductErrorCode.SPEC_TEMPLATE_NOT_FOUND);
        }
        return spec;
    }

    private void requireUniqueName(Long shopId, String name, Long excludeId) {
        Long count = merchantSpecMapper.selectCount(Wrappers.<MerchantSpec>lambdaQuery()
                .eq(MerchantSpec::getShopId, shopId)
                .eq(MerchantSpec::getName, name.trim())
                .ne(excludeId != null, MerchantSpec::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(40003, "同名规格已存在");
        }
    }

    /** 规格值去空白去重（保序）后存为 JSON 数组 */
    private String writeOptions(List<String> options) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String option : options) {
            if (StringUtils.hasText(option)) {
                distinct.add(option.trim());
            }
        }
        if (distinct.isEmpty()) {
            throw new BusinessException(40003, "请至少提供一个规格值");
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(distinct));
        } catch (Exception ex) {
            throw new BusinessException(40003, "规格值序列化失败，请重试");
        }
    }

    private List<String> readOptions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private SpecDtos.SpecTemplateVO toVO(MerchantSpec spec) {
        SpecDtos.SpecTemplateVO vo = new SpecDtos.SpecTemplateVO();
        vo.setId(spec.getId());
        vo.setName(spec.getName());
        vo.setSort(spec.getSort());
        vo.setOptions(readOptions(spec.getOptions()));
        return vo;
    }
}
