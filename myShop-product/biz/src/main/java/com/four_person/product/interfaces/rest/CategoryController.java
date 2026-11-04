package com.four_person.product.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.product.api.dto.CategoryRequest;
import com.four_person.product.api.dto.CategoryVO;
import com.four_person.product.application.CategoryApplicationService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryApplicationService categoryApplicationService;

    /** 买家端：分类树（大类 + 小类） */
    @GetMapping
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryApplicationService.tree());
    }

    /** 买家端：平铺分类 */
    @GetMapping("/all")
    public Result<List<CategoryVO>> listAll() {
        return Result.success(categoryApplicationService.listAll());
    }

    /** 平台：新增分类 */
    @PostMapping
    public Result<CategoryVO> create(@Valid @RequestBody CategoryRequest request) {
        AccessGuard.requirePerm("admin:categories:create");
        return Result.success("分类已新增", categoryApplicationService.create(request));
    }

    /** 平台：修改分类 */
    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        AccessGuard.requirePerm("admin:categories:update");
        return Result.success("分类已更新", categoryApplicationService.update(id, request));
    }

    /** 平台：删除分类 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requirePerm("admin:categories:delete");
        categoryApplicationService.delete(id);
        return Result.success("分类已删除", null);
    }
}
