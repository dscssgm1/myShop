package com.four_person.order.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨店合单订单组（父订单）：优惠记在组上按子单比例分摊，支付/取消以组为单位。
 */
@Data
public class OrderGroupVO {

    private String groupNo;

    private String status;

    private String statusText;

    /** 子订单数量（店铺数） */
    private Integer shopCount;

    private BigDecimal originalAmount;

    private BigDecimal promotionDiscount;

    private BigDecimal couponDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal merchantBearAmount;

    private BigDecimal platformBearAmount;

    /** 整组抵现消耗的积分 */
    private Integer pointsUsed;

    /** 整组积分抵现金额（平台承担） */
    private BigDecimal pointsDeduction;

    private BigDecimal freightAmount;

    /** 组应付总额 = 各子单分摊后实付之和 */
    private BigDecimal payAmount;

    private String promotionTitle;

    private String couponTitle;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    /** 券核销时使用的订单号（第一张子单），释放券时按此幂等退回 */
    private String couponUseOrderNo;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private LocalDateTime closedTime;

    private LocalDateTime createdAt;

    /** 组内子订单（含分摊后的金额与商品明细） */
    private List<OrderVO> orders;
}
