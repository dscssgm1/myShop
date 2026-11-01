package com.four_person.product.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.product.api.dto.CategoryVO;
import com.four_person.product.application.CategoryApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryApplicationService categoryApplicationService;

    @GetMapping
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryApplicationService.listAll());
    }
}
