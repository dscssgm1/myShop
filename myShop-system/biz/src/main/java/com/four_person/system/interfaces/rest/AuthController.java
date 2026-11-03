package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.application.AuthApplicationService;
import com.four_person.system.infrastructure.security.SecurityUser;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;

    @PostMapping("/register")
    public Result<AuthDtos.LoginResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return Result.success("注册成功", authApplicationService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return Result.success("登录成功", authApplicationService.login(request));
    }

    /**
     * 退出登录：删除当前会话，令牌立即失效（网关下次请求即拒绝）。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityUser user = SecurityUtils.currentUser();
        authApplicationService.logout(user.getId(), user.getSessionId());
        return Result.success("已退出登录", null);
    }

    /** 当前账号的在线登录设备 */
    @GetMapping("/sessions")
    public Result<List<AuthDtos.SessionVO>> sessions() {
        SecurityUser user = SecurityUtils.currentUser();
        return Result.success(authApplicationService.sessions(user.getId(), user.getSessionId()));
    }

    /** 单点下线：踢掉自己的一台设备 */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> kickSession(@PathVariable String sessionId) {
        SecurityUser user = SecurityUtils.currentUser();
        authApplicationService.kickSession(user.getId(), sessionId);
        return Result.success("该设备已下线", null);
    }

    /** 下线当前账号的全部设备 */
    @DeleteMapping("/sessions")
    public Result<Void> kickAllSessions() {
        authApplicationService.kickAll(SecurityUtils.currentUserId());
        return Result.success("所有设备已下线", null);
    }

    @GetMapping("/me")
    public Result<UserDtos.UserVO> me() {
        return Result.success(authApplicationService.me(SecurityUtils.currentUserId()));
    }

    @PostMapping("/email-code")
    public Result<AuthDtos.EmailCodeResponse> sendEmailCode(@Valid @RequestBody AuthDtos.EmailCodeRequest request) {
        return Result.success("验证码已发送", authApplicationService.sendEmailCode(request));
    }

    /** 忘记密码：向邮箱发送验证码 */
    @PostMapping("/password/forgot")
    public Result<AuthDtos.EmailCodeResponse> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        return Result.success("验证码已发送", authApplicationService.forgotPassword(request));
    }

    /** 通过邮箱验证码重置密码 */
    @PostMapping("/password/reset")
    public Result<AuthDtos.DeviceVerifyTokenVO> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        return Result.success("密码已重置，请重新登录", authApplicationService.resetPassword(request));
    }
}
