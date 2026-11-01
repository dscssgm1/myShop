package com.four_person.product.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.product.api.dto.SpecDtos;
import com.four_person.product.domain.model.SpecOption;
import com.four_person.product.domain.model.SpecTemplate;
import com.four_person.product.enums.ProductErrorCode;
import com.four_person.product.infrastructure.persistence.mapper.SpecOptionMapper;
import com.four_person.product.infrastructure.persistence.mapper.SpecTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecApplicationService {

    private final SpecTemplateMapper templateMapper;
    private final SpecOptionMapper optionMapper;

    public List<SpecDtos.SpecTemplateVO> list() {
        return templateMapper.selectList(Wrappers.<SpecTemplate>lambdaQuery()
                        .orderByAsc(SpecTemplate::getSort).orderByAsc(SpecTemplate::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public SpecDtos.SpecTemplateVO create(SpecDtos.SpecTemplateRequest request) {
        SpecTemplate template = new SpecTemplate();
        template.setName(request.getName());
        template.setSort(request.getSort() == null ? 0 : request.getSort());
        templateMapper.insert(template);
        saveOptions(template.getId(), request.getOptions());
        return toVO(template);
    }

    @Transactional
    public SpecDtos.SpecTemplateVO update(Long id, SpecDtos.SpecTemplateRequest request) {
        SpecTemplate template = require(id);
        template.setName(request.getName());
        if (request.getSort() != null) {
            template.setSort(request.getSort());
        }
        templateMapper.updateById(template);
        // 选项整体替换
        optionMapper.delete(Wrappers.<SpecOption>lambdaQuery().eq(SpecOption::getTemplateId, id));
        saveOptions(id, request.getOptions());
        return toVO(template);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        optionMapper.delete(Wrappers.<SpecOption>lambdaQuery().eq(SpecOption::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    private void saveOptions(Long templateId, List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            SpecOption option = new SpecOption();
            option.setTemplateId(templateId);
            option.setValue(options.get(i));
            option.setSort(i + 1);
            optionMapper.insert(option);
        }
    }

    private SpecTemplate require(Long id) {
        SpecTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ProductErrorCode.SPEC_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private SpecDtos.SpecTemplateVO toVO(SpecTemplate template) {
        SpecDtos.SpecTemplateVO vo = new SpecDtos.SpecTemplateVO();
        vo.setId(template.getId());
        vo.setName(template.getName());
        vo.setSort(template.getSort());
        vo.setOptions(optionMapper.selectList(Wrappers.<SpecOption>lambdaQuery()
                        .eq(SpecOption::getTemplateId, template.getId())
                        .orderByAsc(SpecOption::getSort))
                .stream().map(SpecOption::getValue).toList());
        return vo;
    }
}
