package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.system.domain.model.RoleMenu;
import com.four_person.system.domain.repository.RoleMenuRepository;
import com.four_person.system.infrastructure.persistence.mapper.RoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RoleMenuRepositoryImpl implements RoleMenuRepository {

    private final RoleMenuMapper mapper;

    @Override
    public List<String> findKeysByRole(String roleCode) {
        return mapper.selectList(Wrappers.<RoleMenu>lambdaQuery()
                        .eq(RoleMenu::getRoleCode, roleCode))
                .stream().map(RoleMenu::getPermKey).sorted().toList();
    }

    @Override
    public void deleteByRole(String roleCode) {
        mapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleCode, roleCode));
    }

    @Override
    public void insert(RoleMenu roleMenu) {
        mapper.insert(roleMenu);
    }
}
