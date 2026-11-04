package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.system.api.dto.RoleDtos;
import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.enums.SystemErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleApplicationService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    /** 全量角色列表；groupCode 传入时按端过滤（员工管理下拉只列本端角色） */
    public List<RoleDtos.RoleVO> list(String groupCode) {
        return roleRepository.findAll().stream()
                .filter(r -> groupCode == null || groupCode.isBlank() || groupCode.equals(r.getGroupCode()))
                .map(this::toVO).toList();
    }

    public RoleDtos.RoleVO create(RoleDtos.RoleRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw new BusinessException(SystemErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        apply(role, request);
        // 归属端强制取编辑者所属端，防止跨端造角色
        role.setGroupCode(UserContext.getGroup());
        roleRepository.save(role);
        return toVO(role);
    }

    public RoleDtos.RoleVO update(Long id, RoleDtos.RoleRequest request) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ROLE_NOT_FOUND));
        requireSameGroup(role);
        String originalGroup = role.getGroupCode();
        // 角色编码是权限标识，创建后不允许修改；归属端也不随请求变化
        apply(role, request);
        role.setGroupCode(originalGroup);
        roleRepository.save(role);
        return toVO(role);
    }

    public void delete(Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ROLE_NOT_FOUND));
        requireSameGroup(role);
        if (List.of("admin", "merchant").contains(role.getCode())) {
            throw new BusinessException(40304, "内置管理员角色不能删除");
        }
        if (userRepository.countByRole(role.getCode()) > 0) {
            throw new BusinessException(SystemErrorCode.ROLE_IN_USE);
        }
        roleRepository.deleteById(id);
    }

    /** 只能操作本端角色 */
    private void requireSameGroup(SysRole role) {
        String editorGroup = UserContext.getGroup();
        if (editorGroup == null || !editorGroup.equals(role.getGroupCode())) {
            throw new BusinessException(40302, "只能操作本端角色");
        }
    }

    private void apply(SysRole role, RoleDtos.RoleRequest request) {
        if (role.getId() == null) {
            role.setCode(request.getCode());
        }
        role.setName(request.getName());
        role.setGroupCode(request.getGroupCode());
        role.setIcon(request.getIcon());
        role.setDescription(request.getDescription());
        role.setSort(request.getSort() == null ? 0 : request.getSort());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private RoleDtos.RoleVO toVO(SysRole role) {
        RoleDtos.RoleVO vo = new RoleDtos.RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setGroupCode(role.getGroupCode());
        vo.setIcon(role.getIcon());
        vo.setDescription(role.getDescription());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        vo.setUserCount(userRepository.countByRole(role.getCode()));
        return vo;
    }
}
