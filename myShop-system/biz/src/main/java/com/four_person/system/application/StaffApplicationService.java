package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.system.api.dto.StaffDtos;
import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.enums.SystemErrorCode;
import com.four_person.system.infrastructure.support.AuthSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 员工账号管理（sys_user 真实登录账号，区别于花名册）：
 * 商家按本店圈定（shop_id = 当前店铺，角色限 MERCHANT 组），平台管理 PLATFORM 组账号。
 * 角色变更即时刷新在线会话（角色 + 权限集），禁用/删除/重置密码踢下线。
 */
@Service
@RequiredArgsConstructor
public class StaffApplicationService {

    /** 锚点角色：店铺/平台的管理员角色，其账号不允许被删除或停用 */
    private static final List<String> ANCHOR_ROLES = List.of("admin", "merchant");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleMenuApplicationService roleMenuApplicationService;
    private final AuthSessionStore sessionStore;
    private final PasswordEncoder passwordEncoder;

    public List<StaffDtos.StaffVO> list() {
        String group = requireStaffManager();
        List<SysRole> roles = groupRoles(group);
        List<SysUser> users = userRepository.findByRoleCodes(roles.stream().map(SysRole::getCode).toList());
        if ("MERCHANT".equals(group)) {
            Long shopId = UserContext.requireShopId();
            users = users.stream().filter(u -> shopId.equals(u.getShopId())).toList();
        }
        return users.stream().map(this::toVO).toList();
    }

    public StaffDtos.StaffVO create(StaffDtos.CreateRequest request) {
        String group = requireStaffManager();
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(SystemErrorCode.USERNAME_EXISTS);
        }
        SysRole role = requireGroupRole(group, request.getRoleCode());

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRoleCode(role.getCode());
        if ("MERCHANT".equals(group)) {
            user.setShopId(UserContext.requireShopId());
        }
        user.setStatus(1);
        userRepository.save(user);
        return toVO(user);
    }

    public StaffDtos.StaffVO update(Long id, StaffDtos.UpdateRequest request) {
        String group = requireStaffManager();
        SysUser user = requireInScope(group, id);

        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (StringUtils.hasText(request.getRoleCode()) && !request.getRoleCode().equals(user.getRoleCode())) {
            if (isAnchor(user.getRoleCode())) {
                throw new BusinessException(40304, "管理员账号不能调整角色");
            }
            SysRole role = requireGroupRole(group, request.getRoleCode());
            user.setRoleCode(role.getCode());
            // 角色/权限集即时刷新；商家员工同时刷新店铺绑定（角色决定归属店铺）
            sessionStore.updateAttributes(id, Map.of(
                    "role", role.getCode(),
                    "group", role.getGroupCode(),
                    "shopId", user.getShopId() == null ? "" : String.valueOf(user.getShopId()),
                    "perms", roleMenuApplicationService.permsJoined(role.getCode())));
        }
        userRepository.save(user);
        return toVO(user);
    }

    public StaffDtos.StaffVO updateStatus(Long id, Integer status) {
        String group = requireStaffManager();
        SysUser user = requireInScope(group, id);
        if (user.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(40305, "不能停用自己");
        }
        if (status != null && status == 0 && isAnchor(user.getRoleCode())) {
            throw new BusinessException(40304, "管理员账号不能停用");
        }
        user.setStatus(status == null ? 1 : status);
        userRepository.save(user);
        if (user.getStatus() == 0) {
            sessionStore.deleteAll(id);
        }
        return toVO(user);
    }

    public void resetPassword(Long id, StaffDtos.ResetPasswordRequest request) {
        String group = requireStaffManager();
        SysUser user = requireInScope(group, id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        // 密码被重置，踢全部设备强制重新登录
        sessionStore.deleteAll(id);
    }

    public void delete(Long id) {
        String group = requireStaffManager();
        SysUser user = requireInScope(group, id);
        if (user.getId().equals(UserContext.getUserId())) {
            throw new BusinessException(40305, "不能删除自己");
        }
        if (isAnchor(user.getRoleCode())) {
            throw new BusinessException(40304, "管理员账号不能删除");
        }
        userRepository.delete(id);
        sessionStore.deleteAll(id);
    }

    // ==================== 内部逻辑 ====================

    /** 员工管理权限由控制器 requirePerm 保证，这里取编辑者所属端 */
    private String requireStaffManager() {
        String group = UserContext.getGroup();
        if (group == null || !(group.equals("MERCHANT") || group.equals("PLATFORM"))) {
            throw new BusinessException(40302, "无员工管理权限");
        }
        return group;
    }

    private List<SysRole> groupRoles(String group) {
        return roleRepository.findAll().stream()
                .filter(r -> group.equals(r.getGroupCode()))
                .toList();
    }

    /** 角色必须属于编辑者所属端 */
    private SysRole requireGroupRole(String group, String roleCode) {
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ROLE_NOT_FOUND));
        if (!group.equals(role.getGroupCode())) {
            throw new BusinessException(40302, "只能分配本端角色");
        }
        return role;
    }

    /** 目标账号必须在本端（商家还须是本店）范围内 */
    private SysUser requireInScope(String group, Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.USER_NOT_FOUND));
        SysRole role = roleRepository.findByCode(user.getRoleCode()).orElse(null);
        if (role == null || !group.equals(role.getGroupCode())) {
            throw new BusinessException(40302, "员工不存在");
        }
        if ("MERCHANT".equals(group) && !UserContext.requireShopId().equals(user.getShopId())) {
            throw new BusinessException(40302, "员工不存在");
        }
        return user;
    }

    private boolean isAnchor(String roleCode) {
        return ANCHOR_ROLES.contains(roleCode);
    }

    private StaffDtos.StaffVO toVO(SysUser user) {
        StaffDtos.StaffVO vo = new StaffDtos.StaffVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleCode(user.getRoleCode());
        vo.setRoleName(roleRepository.findByCode(user.getRoleCode()).map(SysRole::getName).orElse(user.getRoleCode()));
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
