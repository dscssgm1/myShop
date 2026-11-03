package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 积分商城商品：平台从商品库挑选上架，价格快照，积分价 = 原价 × 配置倍数 */
@Data
@TableName("sys_points_mall_item")
public class PointsMallItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private String productName;
    private String productEmoji;
    private String productColor;
    private BigDecimal productPrice;
    private Long shopId;
    private String shopName;

    /** 1 上架 0 下架 */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
