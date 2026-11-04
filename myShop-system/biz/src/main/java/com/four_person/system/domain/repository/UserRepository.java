package com.four_person.system.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.domain.model.SysUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<SysUser> findById(Long id);

    /** 某角色下的全部用户（角色菜单变更后刷新在线会话用） */
    List<SysUser> findByRoleCode(String roleCode);

    /** 一组角色下的全部用户（员工管理按端圈定范围用） */
    List<SysUser> findByRoleCodes(Collection<String> roleCodes);

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    PageResult<SysUser> page(UserDtos.UserQuery query);

    SysUser save(SysUser user);

    /** 逻辑删除（sys_user.deleted） */
    void delete(Long id);

    void updateLastLogin(Long userId);

    long countByRole(String roleCode);
}
