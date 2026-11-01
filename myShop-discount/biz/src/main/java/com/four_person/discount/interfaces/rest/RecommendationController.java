package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.RecommendationVO;
import com.four_person.discount.application.RecommendationApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationApplicationService recommendationApplicationService;

    @GetMapping
    public Result<List<RecommendationVO>> recommend(@RequestParam(defaultValue = "8") int limit) {
        return Result.success(recommendationApplicationService.recommend(UserContext.getUserId(), limit));
    }
}
