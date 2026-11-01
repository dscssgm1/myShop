package com.four_person.system.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.domain.model.SysUser;

import java.util.Optional;

public interface UserRepository {

    Optional<SysUser> findById(Long id);

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    PageResult<SysUser> page(UserDtos.UserQuery query);

    SysUser save(SysUser user);

    void updateLastLogin(Long userId);

    long countByRole(String roleCode);
}
