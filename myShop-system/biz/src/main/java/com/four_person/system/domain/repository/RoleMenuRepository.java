package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.RoleMenu;

import java.util.List;

public interface RoleMenuRepository {

    /** 角色勾选的全部权限码 */
    List<String> findKeysByRole(String roleCode);

    void deleteByRole(String roleCode);

    void insert(RoleMenu roleMenu);
}
