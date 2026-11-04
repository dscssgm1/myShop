package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private String orderNo;

    private Long shopId;

    private String shopName;

    private String status;

    private String statusText;

    /** NORMAL 普通订单 / RECOVERY 售后补款订单 */
    private String bizType;

    private String bizTypeText;

    /** 补款订单对应的原订单号 */
    private String sourceOrderNo;

    /** 跨店合单订单组号；非空表示合单子订单，取消/支付按组处理 */
    private String groupNo;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    /** 优惠中商家承担的部分 */
    private BigDecimal merchantBearAmount;

    /** 优惠中平台承担（补贴）的部分 */
    private BigDecimal platformBearAmount;

    /** 本单抵现消耗的积分 */
    private Integer pointsUsed;

    /** 本单积分抵现金额（平台承担） */
    private BigDecimal pointsDeduction;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    private String promotionTitle;

    private String couponTitle;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private LocalDateTime completeTime;

    private LocalDateTime cancelTime;

    private LocalDateTime refundTime;

    private LocalDateTime createdAt;

    private List<OrderItemVO> items;
}
