package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.DashboardVO;
import com.four_person.platform.application.DashboardApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardApplicationService dashboardApplicationService;

    /** 平台数据看板 */
    @GetMapping("/overview")
    public Result<DashboardVO> overview() {
        return Result.success(dashboardApplicationService.overview());
    }
}
