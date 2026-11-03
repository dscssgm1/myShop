package com.four_person.order.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long productId;

    /** 下单时快照的商品类目 id；历史订单为 0，结算时回查商品表 */
    private Long categoryId;

    /** SKU，0 表示无规格 */
    private Long skuId;

    private String skuSpecs;

    private String productName;

    private String productEmoji;

    private String productColor;

    private Long shopId;

    private String shopName;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    private LocalDateTime createdAt;
}
