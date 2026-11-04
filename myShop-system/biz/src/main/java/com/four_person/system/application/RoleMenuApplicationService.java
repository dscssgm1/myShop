package com.four_person.system.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.system.domain.model.RoleMenu;
import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.RoleMenuRepository;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.enums.SystemErrorCode;
import com.four_person.system.infrastructure.support.AuthSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 角色菜单/按钮权限：保存勾选并刷新该角色全部在线会话，下一次请求即生效 */
@Service
@RequiredArgsConstructor
public class RoleMenuApplicationService {

    /** 各端权限码前缀（与前端 perm/tree.js 的 key 约定一致） */
    private static final Map<String, String> GROUP_PREFIX = Map.of(
            "PLATFORM", "admin:",
            "MERCHANT", "merchant:");

    private final RoleMenuRepository roleMenuRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuthSessionStore sessionStore;

    /** 角色勾选的全部权限码 */
    public List<String> menusOfRole(String roleCode) {
        return roleMenuRepository.findKeysByRole(roleCode);
    }

    /** 登录会话用：逗号拼接的权限码 */
    public String permsJoined(String roleCode) {
        return String.join(",", menusOfRole(roleCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMenus(String roleCode, List<String> keys) {
        SysRole role = requireEditableRole(roleCode);
        String prefix = GROUP_PREFIX.getOrDefault(role.getGroupCode(), "");

        Set<String> distinct = new HashSet<>();
        for (String key : keys == null ? List.<String>of() : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String trimmed = key.trim();
            if (!trimmed.startsWith(prefix)) {
                throw new BusinessException(40301, "权限码与角色所属端不匹配: " + trimmed);
            }
            distinct.add(trimmed);
        }

        roleMenuRepository.deleteByRole(roleCode);
        for (String key : distinct) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleCode(roleCode);
            rm.setPermKey(key);
            roleMenuRepository.insert(rm);
        }

        // 该角色全部在线会话刷新权限集，网关下一次请求即下发新权限
        String joined = String.join(",", distinct);
        for (SysUser user : userRepository.findByRoleCode(roleCode)) {
            sessionStore.updateAttributes(user.getId(), Map.of("perms", joined));
        }
    }

    /** 目标角色必须与本端同组，且不允许修改自己所属角色（防自锁） */
    private SysRole requireEditableRole(String roleCode) {
        SysRole role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ROLE_NOT_FOUND));
        String editorGroup = UserContext.getGroup();
        if (editorGroup == null || !editorGroup.equals(role.getGroupCode())) {
            throw new BusinessException(40302, "只能配置本端角色");
        }
        if (roleCode.equals(UserContext.getRole())) {
            throw new BusinessException(40303, "不能修改自己所属角色的权限");
        }
        return role;
    }
}
