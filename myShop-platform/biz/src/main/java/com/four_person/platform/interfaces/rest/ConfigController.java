package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.ConfigApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigApplicationService configApplicationService;

    /** 读取配置组：banner / pay-method / aftersale-reason / report-type / brand / sensitive-word */
    @GetMapping("/{group}")
    public Result<List<PlatformDtos.ConfigItemVO>> list(@PathVariable String group) {
        return Result.success(configApplicationService.list(group));
    }

    @PutMapping("/{group}")
    public Result<List<PlatformDtos.ConfigItemVO>> save(@PathVariable String group,
                                                        @RequestBody PlatformDtos.ConfigSaveRequest request) {
        return Result.success("配置已保存", configApplicationService.save(group, request));
    }
}
