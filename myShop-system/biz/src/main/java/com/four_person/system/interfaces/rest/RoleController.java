package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.system.api.dto.RoleDtos;
import com.four_person.system.api.dto.StaffDtos;
import com.four_person.system.application.RoleApplicationService;
import com.four_person.system.application.RoleMenuApplicationService;
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
 * 角色管理与菜单配置：
 * <ul>
 *     <li>列表：商家/平台端均可读（员工分配角色下拉、用户管理筛选共用）；</li>
 *     <li>增删改/配置菜单：需「角色管理」权限码，且只能操作本端角色；</li>
 *     <li>菜单配置保存后该角色在线会话的权限集立即刷新。</li>
 * </ul>
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleApplicationService roleApplicationService;
    private final RoleMenuApplicationService roleMenuApplicationService;

    @GetMapping
    public Result<List<RoleDtos.RoleVO>> list(@RequestParam(required = false) String groupCode) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        return Result.success(roleApplicationService.list(groupCode));
    }

    @PostMapping
    public Result<RoleDtos.RoleVO> create(@Valid @RequestBody RoleDtos.RoleRequest request) {
        AccessGuard.requirePerm("merchant:roles:save", "admin:roles:save");
        return Result.success("角色已创建", roleApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<RoleDtos.RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleDtos.RoleRequest request) {
        AccessGuard.requirePerm("merchant:roles:save", "admin:roles:save");
        return Result.success("角色已更新", roleApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AccessGuard.requirePerm("merchant:roles:delete", "admin:roles:delete");
        roleApplicationService.delete(id);
        return Result.success("角色已删除", null);
    }

    /** 角色已勾选的菜单/按钮权限码（角色配置弹窗回显） */
    @GetMapping("/{code}/menus")
    public Result<List<String>> menus(@PathVariable String code) {
        AccessGuard.requirePerm("merchant:roles:menus", "admin:roles:menus");
        return Result.success(roleMenuApplicationService.menusOfRole(code));
    }

    /** 保存角色勾选的菜单/按钮，在线员工下一次请求即按新权限生效 */
    @PutMapping("/{code}/menus")
    public Result<Void> saveMenus(@PathVariable String code, @Valid @RequestBody StaffDtos.RoleMenusRequest request) {
        AccessGuard.requirePerm("merchant:roles:menus", "admin:roles:menus");
        roleMenuApplicationService.saveMenus(code, request.getKeys());
        return Result.success("菜单权限已保存并生效", null);
    }
}
