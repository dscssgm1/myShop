package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分兑换记录：兑换成功后生成的兑换订单号回填于此 */
@Data
@TableName("sys_points_exchange")
public class PointsExchange {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long itemId;
    private String itemName;
    private String itemEmoji;
    /** 商品主图快照 */
    private String itemCover;
    private Integer points;
    /** 兑换生成的订单号 */
    private String orderNo;

    private LocalDateTime createdAt;
}
