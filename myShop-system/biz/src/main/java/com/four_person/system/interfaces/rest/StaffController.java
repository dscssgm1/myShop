package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.system.api.dto.StaffDtos;
import com.four_person.system.application.StaffApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 员工账号管理：商家（本店员工）/ 平台（平台员工），角色权限由「角色管理-配置菜单」决定 */
@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffApplicationService staffApplicationService;

    @GetMapping
    public Result<List<StaffDtos.StaffVO>> list() {
        AccessGuard.requirePerm("merchant:staff", "admin:staff");
        return Result.success(staffApplicationService.list());
    }

    @PostMapping
    public Result<StaffDtos.StaffVO> create(@Valid @RequestBody StaffDtos.CreateRequest request) {
        AccessGuard.requirePerm("merchant:staff:create", "admin:staff:create");
        return Result.success("员工已添加", staffApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<StaffDtos.StaffVO> update(@PathVariable Long id,
                                            @Valid @RequestBody StaffDtos.UpdateRequest request) {
        AccessGuard.requirePerm("merchant:staff:update", "admin:staff:update");
        return Result.success("员工已更新", staffApplicationService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public Result<StaffDtos.StaffVO> updateStatus(@PathVariable Long id, Integer status) {
        AccessGuard.requirePerm("merchant:staff:status", "admin:staff:status");
        return Result.success(staffApplicationService.updateStatus(id, status));
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody StaffDtos.ResetPasswordRequest request) {
        AccessGuard.requirePerm("merchant:staff:password", "admin:staff:password");
        staffApplicationService.resetPassword(id, request);
        return Result.success("密码已重置，该员工需重新登录", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requirePerm("merchant:staff:delete", "admin:staff:delete");
        staffApplicationService.delete(id);
        return Result.success("员工已删除", null);
    }
}
