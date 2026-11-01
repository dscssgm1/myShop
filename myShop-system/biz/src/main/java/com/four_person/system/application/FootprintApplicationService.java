package com.four_person.system.application;

import com.four_person.system.api.dto.AssetDtos;
import com.four_person.system.domain.model.UserFootprint;
import com.four_person.system.domain.repository.FootprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FootprintApplicationService {

    private final FootprintRepository footprintRepository;

    public List<AssetDtos.FootprintVO> list(Long userId, int limit) {
        return footprintRepository.findByUserId(userId, limit).stream().map(this::toVO).toList();
    }

    public void record(Long userId, Long productId) {
        footprintRepository.record(userId, productId);
    }

    public void clear(Long userId) {
        footprintRepository.clear(userId);
    }

    private AssetDtos.FootprintVO toVO(UserFootprint footprint) {
        AssetDtos.FootprintVO vo = new AssetDtos.FootprintVO();
        vo.setProductId(footprint.getProductId());
        vo.setViewedAt(footprint.getViewedAt());
        return vo;
    }
}
