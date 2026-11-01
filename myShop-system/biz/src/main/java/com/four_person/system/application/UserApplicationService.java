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
import com.four_person.system.infrastructure.support.VerificationCodeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationCodeStore verificationCodeStore;
    private final PasswordEncoder passwordEncoder;

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
        return toVO(user);
    }

    public void changePassword(Long userId, AuthDtos.ChangePasswordRequest request) {
        SysUser user = require(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(SystemErrorCode.OLD_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
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
        verificationCodeStore.invalidate(request.getEmail(), "CHANGE_EMAIL");

        user.setEmail(request.getEmail());
        userRepository.save(user);
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
    }

    public void updateRole(Long id, String roleCode) {
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ROLE_NOT_FOUND));
        SysUser user = require(id);
        user.setRoleCode(role.getCode());
        userRepository.save(user);
    }
}
