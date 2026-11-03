package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.platform.application.RegionApplicationService;
import com.four_person.platform.application.RegionDtos;
import com.four_person.platform.application.RegionDtos.TreeNodeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行政区划：地址选择器读公开树，配置中心增删改走管理接口。
 */
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class RegionController {

    private final RegionApplicationService regionApplicationService;

    /** 买家端：启用节点的完整树（网关 /api/config/public/ 白名单放行，与 ConfigController 同前缀） */
    @GetMapping("/public/regions/tree")
    public Result<List<TreeNodeVO>> publicTree() {
        return Result.success(regionApplicationService.publicTree());
    }

    /** 平台管理员：完整树（含停用节点） */
    @GetMapping("/regions/tree")
    public Result<List<TreeNodeVO>> tree() {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(regionApplicationService.tree());
    }

    @PostMapping("/regions")
    public Result<TreeNodeVO> create(@Valid @RequestBody RegionDtos.RegionRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("区域已新增", regionApplicationService.create(request));
    }

    @PutMapping("/regions/{id}")
    public Result<TreeNodeVO> rename(@PathVariable Long id,
                                     @Valid @RequestBody RegionDtos.RegionRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("区域名称已更新", regionApplicationService.rename(id, request));
    }

    @PutMapping("/regions/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        AccessGuard.requireGroup("PLATFORM");
        regionApplicationService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @DeleteMapping("/regions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requireGroup("PLATFORM");
        regionApplicationService.delete(id);
        return Result.success("区域已删除", null);
    }
}
