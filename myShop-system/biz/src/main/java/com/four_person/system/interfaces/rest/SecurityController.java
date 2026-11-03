package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.ProfileDtos;
import com.four_person.system.application.SecurityApplicationService;
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

/**
 * 账号安全：登录记录、信任设备、身份验证。
 */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityApplicationService securityApplicationService;

    @GetMapping("/security")
    public Result<ProfileDtos.SecurityOverviewVO> overview() {
        return Result.success(securityApplicationService.overview(SecurityUtils.currentUserId()));
    }

    /** 向当前账号邮箱发送身份验证码 */
    @PostMapping("/verify-identity/code")
    public Result<AuthDtos.EmailCodeResponse> sendIdentityCode() {
        return Result.success("验证码已发送",
                securityApplicationService.sendIdentityCode(SecurityUtils.currentUserId()));
    }

    /** 校验身份验证码，返回短时效身份验证凭证 */
    @PostMapping("/verify-identity")
    public Result<ProfileDtos.VerifyIdentityResponse> verifyIdentity(
            @Valid @RequestBody ProfileDtos.VerifyIdentityRequest request) {
        return Result.success("身份验证通过",
                securityApplicationService.verifyIdentity(SecurityUtils.currentUserId(), request.getCode()));
    }

    /** 将当前设备标记为信任设备（需身份验证凭证） */
    @PostMapping("/trusted-devices")
    public Result<ProfileDtos.TrustedDeviceVO> trustDevice(
            @Valid @RequestBody ProfileDtos.TrustDeviceRequest request) {
        return Result.success("已信任此设备",
                securityApplicationService.trustDevice(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/trusted-devices/{id}")
    public Result<Void> removeTrustedDevice(@PathVariable Long id) {
        securityApplicationService.removeTrustedDevice(SecurityUtils.currentUserId(), id);
        return Result.success("已移除信任设备", null);
    }
}
