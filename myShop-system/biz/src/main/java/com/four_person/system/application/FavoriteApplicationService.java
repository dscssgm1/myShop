package com.four_person.system.application;

import com.four_person.system.api.dto.AssetDtos;
import com.four_person.system.domain.model.UserFavorite;
import com.four_person.system.domain.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteApplicationService {

    private final FavoriteRepository favoriteRepository;

    public List<AssetDtos.FavoriteVO> list(Long userId) {
        return favoriteRepository.findByUserId(userId).stream().map(this::toVO).toList();
    }

    public boolean exists(Long userId, Long productId) {
        return favoriteRepository.exists(userId, productId);
    }

    public void add(Long userId, Long productId) {
        if (!favoriteRepository.exists(userId, productId)) {
            favoriteRepository.save(userId, productId);
        }
    }

    public void remove(Long userId, Long productId) {
        favoriteRepository.delete(userId, productId);
    }

    private AssetDtos.FavoriteVO toVO(UserFavorite favorite) {
        AssetDtos.FavoriteVO vo = new AssetDtos.FavoriteVO();
        vo.setProductId(favorite.getProductId());
        vo.setCreatedAt(favorite.getCreatedAt());
        return vo;
    }
}
