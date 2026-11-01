package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.UserFootprint;

import java.util.List;

public interface FootprintRepository {

    List<UserFootprint> findByUserId(Long userId, int limit);

    /** 同一商品重复浏览只更新时间 */
    void record(Long userId, Long productId);

    void clear(Long userId);
}
