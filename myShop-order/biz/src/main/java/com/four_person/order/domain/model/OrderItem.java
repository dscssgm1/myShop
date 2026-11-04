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

    /** 商品主图快照（无图商品为 null，前端回退 emoji 占位） */
    private String productCoverImage;

    private Long shopId;

    private String shopName;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    /** 本行促销活动优惠金额（行级分摊，与引擎逐商品明细一致） */
    private BigDecimal promotionDiscount;

    /** 本行优惠券抵扣金额（行级分摊，与引擎逐商品明细一致） */
    private BigDecimal couponDiscount;

    private LocalDateTime createdAt;
}
