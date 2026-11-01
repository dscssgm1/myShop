package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.MembershipRepository;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.enums.SystemErrorCode;
import com.four_person.system.infrastructure.mail.MailService;
import com.four_person.system.infrastructure.security.JwtTokenProvider;
import com.four_person.system.infrastructure.support.ClientIpUtils;
import com.four_person.system.infrastructure.support.SendCodeRateLimiter;
import com.four_person.system.infrastructure.support.VerificationCodeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证用例：注册、登录、邮箱验证码、当前用户。
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private static final String DEFAULT_ROLE = "user";
    private static final int EMAIL_CODE_EXPIRE_MINUTES = 5;
    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final String PURPOSE_RESET_PASSWORD = "RESET_PASSWORD";
    private static final String PURPOSE_CHANGE_EMAIL = "CHANGE_EMAIL";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MembershipRepository membershipRepository;
    private final UserApplicationService userApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final MailService mailService;
    private final VerificationCodeStore verificationCodeStore;
    private final SendCodeRateLimiter sendCodeRateLimiter;

    /** 原型/联调阶段直接把验证码返回给前端；生产环境应关闭并改为邮件发送 */
    @Value("${myshop.security.email-code.debug:true}")
    private boolean emailCodeDebug;

    @Transactional
    public AuthDtos.LoginResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(SystemErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS);
        }
        // 校验邮箱验证码
        verifyCode(request.getEmail(), PURPOSE_REGISTER, request.getEmailCode());

        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRoleCode(DEFAULT_ROLE);
        user.setStatus(1);
        userRepository.save(user);
        membershipRepository.createAccount(user.getId());

        return buildLoginResponse(user);
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(SystemErrorCode.PASSWORD_ERROR));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(SystemErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(SystemErrorCode.USER_DISABLED);
        }
        userRepository.updateLastLogin(user.getId());
        return buildLoginResponse(user);
    }

    public AuthDtos.EmailCodeResponse sendEmailCode(AuthDtos.EmailCodeRequest request) {
        String purpose = StringUtils.hasText(request.getPurpose()) ? request.getPurpose() : PURPOSE_CHANGE_EMAIL;

        // 限流：同一邮箱 60 秒一次、同一 IP 每天上限
        sendCodeRateLimiter.check(request.getEmail(), ClientIpUtils.currentIp());

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        verificationCodeStore.save(request.getEmail(), purpose, code, Duration.ofMinutes(EMAIL_CODE_EXPIRE_MINUTES));

        // 异步发送 HTML 验证码邮件
        mailService.sendVerifyCode(request.getEmail(), code, EMAIL_CODE_EXPIRE_MINUTES);

        AuthDtos.EmailCodeResponse response = new AuthDtos.EmailCodeResponse();
        response.setExpiresIn(EMAIL_CODE_EXPIRE_MINUTES * 60);
        // 调试模式直接把验证码返回给前端；生产环境（debug=false）由邮件投递，返回 null
        response.setCode(emailCodeDebug ? code : null);
        return response;
    }

    /** 忘记密码：校验邮箱存在后发送验证码 */
    public AuthDtos.EmailCodeResponse forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
        AuthDtos.EmailCodeRequest codeRequest = new AuthDtos.EmailCodeRequest();
        codeRequest.setEmail(request.getEmail());
        codeRequest.setPurpose(PURPOSE_RESET_PASSWORD);
        return sendEmailCode(codeRequest);
    }

    /** 通过邮箱验证码重置密码 */
    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        SysUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
        verifyCode(request.getEmail(), PURPOSE_RESET_PASSWORD, request.getCode());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public UserDtos.UserVO me(Long userId) {
        return userApplicationService.profile(userId);
    }

    /** 校验验证码，通过后作废（一次性） */
    private void verifyCode(String email, String purpose, String input) {
        String code = verificationCodeStore.findValid(email, purpose)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.EMAIL_CODE_INVALID));
        if (!code.equals(input)) {
            throw new BusinessException(SystemErrorCode.EMAIL_CODE_INVALID);
        }
        verificationCodeStore.invalidate(email, purpose);
    }

    private AuthDtos.LoginResponse buildLoginResponse(SysUser user) {
        String groupCode = roleRepository.findByCode(user.getRoleCode())
                .map(SysRole::getGroupCode)
                .orElse("USER");

        AuthDtos.LoginResponse response = new AuthDtos.LoginResponse();
        response.setToken(tokenProvider.generate(user, groupCode));
        response.setExpiresIn(tokenProvider.getExpirationSeconds());
        response.setUser(userApplicationService.toVO(user));
        return response;
    }
}
