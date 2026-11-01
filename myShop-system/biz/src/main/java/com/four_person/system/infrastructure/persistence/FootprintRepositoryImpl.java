package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.system.domain.model.UserFootprint;
import com.four_person.system.domain.repository.FootprintRepository;
import com.four_person.system.infrastructure.persistence.mapper.UserFootprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FootprintRepositoryImpl implements FootprintRepository {

    private final UserFootprintMapper mapper;

    @Override
    public List<UserFootprint> findByUserId(Long userId, int limit) {
        Page<UserFootprint> page = mapper.selectPage(new Page<>(1, Math.max(limit, 1)),
                Wrappers.<UserFootprint>lambdaQuery()
                        .eq(UserFootprint::getUserId, userId)
                        .orderByDesc(UserFootprint::getViewedAt));
        return page.getRecords();
    }

    @Override
    public void record(Long userId, Long productId) {
        UserFootprint existing = mapper.selectOne(Wrappers.<UserFootprint>lambdaQuery()
                .eq(UserFootprint::getUserId, userId)
                .eq(UserFootprint::getProductId, productId));
        if (existing == null) {
            UserFootprint footprint = new UserFootprint();
            footprint.setUserId(userId);
            footprint.setProductId(productId);
            footprint.setViewedAt(LocalDateTime.now());
            mapper.insert(footprint);
        } else {
            existing.setViewedAt(LocalDateTime.now());
            mapper.updateById(existing);
        }
    }

    @Override
    public void clear(Long userId) {
        mapper.delete(Wrappers.<UserFootprint>lambdaQuery().eq(UserFootprint::getUserId, userId));
    }
}
