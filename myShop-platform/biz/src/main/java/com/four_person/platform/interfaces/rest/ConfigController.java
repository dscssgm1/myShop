package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.AccessGuard;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.ConfigApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    /** 允许匿名读取的配置组（买家端 Banner、支付方式、服务保障等；原因类预设供商家端弹窗快选） */
    private static final Set<String> PUBLIC_GROUPS =
            Set.of("banner", "pay-method", "aftersale-reason", "report-type", "service-guarantee", "system",
                    "audit-reject-reason", "aftersale-reject-reason", "off-shelf-reason", "shop-disable-reason");

    private final ConfigApplicationService configApplicationService;

    /** 平台管理员：读取配置组 */
    @GetMapping("/{group}")
    public Result<List<PlatformDtos.ConfigItemVO>> list(@PathVariable String group) {
        AccessGuard.requirePerm("admin:config:save");
        return Result.success(configApplicationService.list(group));
    }

    /** 买家端：读取公开配置组（网关白名单放行） */
    @GetMapping("/public/{group}")
    public Result<List<PlatformDtos.ConfigItemVO>> publicList(@PathVariable String group) {
        if (!PUBLIC_GROUPS.contains(group)) {
            throw new BusinessException(404, "配置组不存在或不可公开");
        }
        return Result.success(configApplicationService.list(group));
    }

    @PutMapping("/{group}")
    public Result<List<PlatformDtos.ConfigItemVO>> save(@PathVariable String group,
                                                        @RequestBody PlatformDtos.ConfigSaveRequest request) {
        return Result.success("配置已保存", configApplicationService.save(group, request));
    }

    /** 平台管理员：删除配置项（保存为按 key 覆盖，删除用于移除服务保障等可增删的配置） */
    @DeleteMapping("/{group}/{key}")
    public Result<Void> delete(@PathVariable String group, @PathVariable String key) {
        configApplicationService.deleteItem(group, key);
        return Result.success("配置项已删除", null);
    }
}
