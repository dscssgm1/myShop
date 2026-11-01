package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.SysRole;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    List<SysRole> findAll();

    Optional<SysRole> findById(Long id);

    Optional<SysRole> findByCode(String code);

    boolean existsByCode(String code);

    SysRole save(SysRole role);

    void deleteById(Long id);
}
