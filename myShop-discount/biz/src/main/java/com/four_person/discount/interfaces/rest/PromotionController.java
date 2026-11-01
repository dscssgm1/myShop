package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.discount.api.dto.PromotionRequest;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.application.PromotionApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionApplicationService promotionApplicationService;

    @GetMapping
    public Result<List<PromotionVO>> list(@RequestParam(defaultValue = "active") String status) {
        return Result.success(promotionApplicationService.list(status));
    }

    @PostMapping
    public Result<PromotionVO> create(@Valid @RequestBody PromotionRequest request) {
        return Result.success(promotionApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<PromotionVO> update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return Result.success(promotionApplicationService.update(id, request));
    }

    @PostMapping("/{id}/end")
    public Result<Void> end(@PathVariable Long id) {
        promotionApplicationService.end(id);
        return Result.success();
    }
}
