package com.four_person.system.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PointsMallDtos {

    private PointsMallDtos() {
    }

    @Data
    public static class ItemVO {
        private Long id;
        private Long productId;
        private String productName;
        private String productEmoji;
        private String productColor;
        /** 商品主图快照 */
        private String productCoverImage;
        /** 原价快照 */
        private BigDecimal productPrice;
        /** 积分价 = 原价 × 配置中心倍数 */
        private Integer pointsPrice;
        private Long shopId;
        private String shopName;
        private Integer status;
    }

    @Data
    public static class ItemRequest {
        @NotNull(message = "请选择商品")
        private Long productId;
        private Integer status = 1;
    }

    @Data
    public static class ExchangeRequest {
        @NotNull(message = "请选择兑换商品")
        private Long itemId;
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
    }

    @Data
    public static class ExchangeVO {
        private Long id;
        private Long itemId;
        private String itemName;
        private String itemEmoji;
        /** 商品主图快照 */
        private String itemCover;
        private Integer points;
        private String orderNo;
        private LocalDateTime createdAt;
    }
}
