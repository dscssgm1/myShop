package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.UserRepository;
import com.four_person.system.infrastructure.persistence.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final SysUserMapper mapper;

    @Override
    public Optional<SysUser> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public Optional<SysUser> findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username.trim())));
    }

    @Override
    public Optional<SysUser> findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, email)));
    }

    @Override
    public boolean existsByUsername(String username) {
        return mapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return mapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, email)) > 0;
    }

    @Override
    public PageResult<SysUser> page(UserDtos.UserQuery query) {
        int pageNo = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getSize() == null || query.getSize() < 1 ? 10 : Math.min(query.getSize(), 100);

        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(StringUtils.hasText(query.getRoleCode()), SysUser::getRoleCode, query.getRoleCode())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getId);

        Page<SysUser> page = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public SysUser save(SysUser user) {
        if (user.getId() == null) {
            mapper.insert(user);
        } else {
            mapper.updateById(user);
        }
        return user;
    }

    @Override
    public void updateLastLogin(Long userId) {
        mapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));
    }

    @Override
    public long countByRole(String roleCode) {
        return mapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleCode, roleCode));
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<SysUser> findByRoleCode(String roleCode) {
        return mapper.selectList(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleCode, roleCode));
    }

    @Override
    public List<SysUser> findByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getRoleCode, roleCodes));
    }
}
