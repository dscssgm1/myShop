package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.application.AuthApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 无状态 JWT：退出登录由客户端丢弃令牌完成，服务端仅返回成功。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success("已退出登录", null);
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
    public Result<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authApplicationService.resetPassword(request);
        return Result.success("密码已重置，请重新登录", null);
    }
}
