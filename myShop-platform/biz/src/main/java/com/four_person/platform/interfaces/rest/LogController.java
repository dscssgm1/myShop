package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.LogApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogApplicationService logApplicationService;

    @GetMapping("/operation")
    public Result<PageResult<PlatformDtos.OperationLogVO>> operation(@RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.success(logApplicationService.pageOperation(page, size));
    }

    @GetMapping("/login")
    public Result<PageResult<PlatformDtos.LoginLogVO>> login(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(logApplicationService.pageLogin(page, size));
    }

    @GetMapping("/system")
    public Result<PageResult<PlatformDtos.SystemLogVO>> system(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return Result.success(logApplicationService.pageSystem(page, size));
    }
}
