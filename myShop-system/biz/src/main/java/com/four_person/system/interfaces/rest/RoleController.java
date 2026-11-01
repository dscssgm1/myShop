package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.RoleDtos;
import com.four_person.system.application.RoleApplicationService;
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

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleApplicationService roleApplicationService;

    @GetMapping
    public Result<List<RoleDtos.RoleVO>> list() {
        return Result.success(roleApplicationService.list());
    }

    @PostMapping
    public Result<RoleDtos.RoleVO> create(@Valid @RequestBody RoleDtos.RoleRequest request) {
        return Result.success("角色已创建", roleApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<RoleDtos.RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleDtos.RoleRequest request) {
        return Result.success("角色已更新", roleApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleApplicationService.delete(id);
        return Result.success("角色已删除", null);
    }
}
