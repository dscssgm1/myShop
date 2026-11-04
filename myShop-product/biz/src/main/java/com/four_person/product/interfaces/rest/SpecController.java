package com.four_person.product.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.product.api.dto.SpecDtos;
import com.four_person.product.application.SpecApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/specs")
@RequiredArgsConstructor
public class SpecController {

    private final SpecApplicationService specApplicationService;

    @GetMapping
    public Result<List<SpecDtos.SpecTemplateVO>> list() {
        return Result.success(specApplicationService.list());
    }

    @PostMapping
    public Result<SpecDtos.SpecTemplateVO> create(@Valid @RequestBody SpecDtos.SpecTemplateRequest request) {
        AccessGuard.requirePerm("admin:config:spec");
        return Result.success("规格模板已新增", specApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<SpecDtos.SpecTemplateVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody SpecDtos.SpecTemplateRequest request) {
        AccessGuard.requirePerm("admin:config:spec");
        return Result.success("规格模板已更新", specApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requirePerm("admin:config:spec");
        specApplicationService.delete(id);
        return Result.success("规格模板已删除", null);
    }
}
