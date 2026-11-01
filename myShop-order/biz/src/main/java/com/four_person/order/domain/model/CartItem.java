package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_cart")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    /** SKU，0 表示无规格 */
    private Long skuId;

    /** 规格快照 */
    private String skuSpecs;

    private String productName;

    private BigDecimal price;

    private Integer quantity;

    private Long shopId;

    private String shopName;

    private String emoji;

    private String color;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void increaseQuantity(int delta) {
        this.quantity = (quantity == null ? 0 : quantity) + delta;
    }

    public java.math.BigDecimal subtotal() {
        if (price == null || quantity == null) {
            return java.math.BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return price.multiply(java.math.BigDecimal.valueOf(quantity))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
