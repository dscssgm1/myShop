package com.four_person.system.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户商品资产契约：收藏、浏览足迹（只存商品 ID，商品信息由商品服务提供）。
 */
public final class AssetDtos {

    private AssetDtos() {
    }

    @Data
    public static class ProductRefRequest {

        @NotNull(message = "商品 ID 不能为空")
        private Long productId;
    }

    @Data
    public static class FavoriteVO {

        private Long productId;
        private LocalDateTime createdAt;
    }

    @Data
    public static class FootprintVO {

        private Long productId;
        private LocalDateTime viewedAt;
    }
}
