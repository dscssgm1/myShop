package com.four_person.system.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.application.UserApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;

    /** 平台管理员：分页查询用户 */
    @GetMapping
    public Result<PageResult<UserDtos.UserVO>> page(UserDtos.UserQuery query) {
        AccessGuard.requirePerm("admin:users");
        return Result.success(userApplicationService.page(query));
    }

    @GetMapping("/me")
    public Result<UserDtos.UserVO> me() {
        return Result.success(userApplicationService.profile(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me")
    public Result<UserDtos.UserVO> updateProfile(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return Result.success("保存成功", userApplicationService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        userApplicationService.changePassword(SecurityUtils.currentUserId(), request);
        return Result.success("密码修改成功", null);
    }

    @PutMapping("/me/email")
    public Result<Void> changeEmail(@Valid @RequestBody AuthDtos.ChangeEmailRequest request) {
        userApplicationService.changeEmail(SecurityUtils.currentUserId(), request);
        return Result.success("邮箱修改成功", null);
    }

    /** 平台管理员：启用 / 禁用用户 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateStatusRequest request) {
        AccessGuard.requirePerm("admin:users:status");
        userApplicationService.updateStatus(id, request.getStatus());
        return Result.success("状态已更新", null);
    }

    /** 平台管理员：调整用户角色 */
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateRoleRequest request) {
        AccessGuard.requirePerm("admin:users:role");
        userApplicationService.updateRole(id, request.getRoleCode());
        return Result.success("角色已更新", null);
    }

    /** 平台管理员：查看用户在线登录设备 */
    @GetMapping("/{id}/sessions")
    public Result<List<AuthDtos.SessionVO>> sessions(@PathVariable Long id) {
        AccessGuard.requirePerm("admin:users:session");
        return Result.success(userApplicationService.sessions(id, SecurityUtils.currentUser().getSessionId()));
    }

    /** 平台管理员：单点下线用户的指定设备 */
    @DeleteMapping("/{id}/sessions/{sessionId}")
    public Result<Void> kickSession(@PathVariable Long id, @PathVariable String sessionId) {
        AccessGuard.requirePerm("admin:users:session");
        userApplicationService.kickSession(id, sessionId);
        return Result.success("该设备已下线", null);
    }
}
