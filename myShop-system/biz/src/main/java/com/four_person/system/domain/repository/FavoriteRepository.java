package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.UserFavorite;

import java.util.List;

public interface FavoriteRepository {

    List<UserFavorite> findByUserId(Long userId);

    boolean exists(Long userId, Long productId);

    void save(Long userId, Long productId);

    void delete(Long userId, Long productId);
}
