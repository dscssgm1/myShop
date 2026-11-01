package com.four_person.discount.controller;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.PushResultVO;
import com.four_person.discount.application.PushApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final PushApplicationService pushApplicationService;

    @GetMapping("/promotions")
    public Result<PushResultVO> push() {
        return Result.success(pushApplicationService.push(UserContext.getUserId()));
    }
}
