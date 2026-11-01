package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.LogApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：其他服务写入日志，不经过网关暴露。
 */
@RestController
@RequestMapping("/internal/logs")
@RequiredArgsConstructor
public class InternalLogController {

    private final LogApplicationService logApplicationService;

    @PostMapping("/operation")
    public Result<Void> writeOperation(@RequestBody PlatformDtos.OperationLogRequest request) {
        logApplicationService.recordOperation(request);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<Void> writeLogin(@RequestBody PlatformDtos.LoginLogRequest request) {
        logApplicationService.recordLogin(request);
        return Result.success();
    }

    @PostMapping("/system")
    public Result<Void> writeSystem(@RequestBody PlatformDtos.SystemLogRequest request) {
        logApplicationService.recordSystem(request);
        return Result.success();
    }
}
