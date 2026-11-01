package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.system.domain.model.SysRole;
import com.four_person.system.domain.repository.RoleRepository;
import com.four_person.system.infrastructure.persistence.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final SysRoleMapper mapper;

    @Override
    public List<SysRole> findAll() {
        return mapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getId));
    }

    @Override
    public Optional<SysRole> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<SysRole> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, code)));
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, code)) > 0;
    }

    @Override
    public SysRole save(SysRole role) {
        if (role.getId() == null) {
            mapper.insert(role);
        } else {
            mapper.updateById(role);
        }
        return role;
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }
}
