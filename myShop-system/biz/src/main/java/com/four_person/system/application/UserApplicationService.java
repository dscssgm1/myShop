package com.four_person.system.application;

import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.AuthDtos;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.enums.SystemErrorCode;
import com.four_person.system.infrastructure.security.JwtTokenProvider;
import com.four_person.system.infrastructure.security.SecurityUser;
import com.four_person.system.infrastructure.security.SecurityUtils;
import com.four_person.system.infrastructure.support.AuthSessionStore;
import com.four_person.system.infrastructure.support.DeviceUtils;
import com.four_person.system.infrastructure.support.VerificationCodeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationCodeStore verificationCodeStore;
    private final PasswordEncoder passwordEncoder;
    private final SecurityApplicationService securityApplicationService;
    private final JwtTokenProvider tokenProvider;
    private final AuthSessionStore sessionStore;

    public SysUser require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
    }

    public UserDtos.UserVO toVO(SysUser user) {
        UserDtos.UserVO vo = new UserDtos.UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRoleCode(user.getRoleCode());
        vo.setShopId(user.getShopId());
        vo.setCompanyId(user.getCompanyId());
        vo.setCourierId(user.getCourierId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        roleRepository.findByCode(user.getRoleCode()).ifPresent(role -> {
            vo.setRoleName(role.getName());
            vo.setGroupCode(role.getGroupCode());
        });
        return vo;
    }

    public UserDtos.UserVO profile(Long userId) {
        return toVO(require(userId));
    }

    public UserDtos.UserVO updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        SysUser user = require(userId);
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        userRepository.save(user);
        // 昵称会被网关注入下游，同步刷新在线会话，避免展示旧昵称
        sessionStore.updateAttributes(userId, Map.of("nickname", user.getNickname() == null ? "" : user.getNickname()));
        return toVO(user);
    }

    public void changePassword(Long userId, AuthDtos.ChangePasswordRequest request) {
        SysUser user = require(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(SystemErrorCode.OLD_PASSWORD_ERROR);
        }
        // 信任设备免邮箱身份验证；未信任设备需出示身份验证凭证
        requireIdentityVerified(userId, request.getVerifyToken());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // 改密后其它设备强制下线，仅保留当前设备
        SecurityUser current = SecurityUtils.currentUser();
        sessionStore.deleteAllExcept(userId, current != null ? current.getSessionId() : null);
    }

    @Transactional
    public void changeEmail(Long userId, AuthDtos.ChangeEmailRequest request) {
        SysUser user = require(userId);
        if (request.getEmail().equalsIgnoreCase(user.getEmail())) {
            // 未发生变更，无需重复校验
            return;
        }
        String code = verificationCodeStore.findValid(request.getEmail(), "CHANGE_EMAIL")
                .orElseThrow(() -> new BusinessException(SystemErrorCode.EMAIL_CODE_INVALID));
        if (!code.equals(request.getCode())) {
            throw new BusinessException(SystemErrorCode.EMAIL_CODE_INVALID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS);
        }
        requireIdentityVerified(userId, request.getVerifyToken());
        verificationCodeStore.invalidate(request.getEmail(), "CHANGE_EMAIL");

        user.setEmail(request.getEmail());
        userRepository.save(user);
    }

    /**
     * 敏感操作身份校验：信任设备直接放行；
     * 未信任设备必须出示有效的短时效身份验证凭证（向当前邮箱验证后获得）。
     */
    private void requireIdentityVerified(Long userId, String verifyToken) {
        if (securityApplicationService.isTrusted(userId, DeviceUtils.currentDeviceId())) {
            return;
        }
        Long tokenUserId = tokenProvider.parseDeviceTokenUserId(verifyToken);
        if (tokenUserId != null && tokenUserId.equals(userId)) {
            return;
        }
        throw new BusinessException(SystemErrorCode.IDENTITY_VERIFY_REQUIRED);
    }

    public PageResult<UserDtos.UserVO> page(UserDtos.UserQuery query) {
        PageResult<SysUser> page = userRepository.page(query);
        return PageResult.of(
                page.getList().stream().map(this::toVO).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public void updateStatus(Long id, Integer status) {
        SysUser user = require(id);
        user.setStatus(status);
        userRepository.save(user);
        // 禁用账号立即踢下线，网关读取不到会话后直接返回 401
        if (status == null || status != 1) {
            sessionStore.deleteAll(id);
        }
    }

    public void updateRole(Long id, String roleCode) {
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ROLE_NOT_FOUND));
        SysUser user = require(id);
        user.setRoleCode(role.getCode());
        userRepository.save(user);
        // 角色变更立即生效：刷新在线会话中的身份属性，网关下次请求即使用新角色
        sessionStore.updateAttributes(id, Map.of("role", role.getCode(), "group", role.getGroupCode()));
    }

    // ==================== 平台管理员：登录设备管理 ====================

    /** 查看指定用户的在线设备 */
    public List<AuthDtos.SessionVO> sessions(Long userId, String currentSessionId) {
        require(userId);
        return sessionStore.list(userId, currentSessionId);
    }

    /** 平台管理员：单点下线指定用户的一台设备 */
    public void kickSession(Long userId, String sessionId) {
        sessionStore.delete(userId, sessionId);
    }
}
