package com.four_person.order.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderVO {

    private String orderNo;

    private BigDecimal payAmount;

    /** 跨店合单订单组号（多店铺结算时非空，整组一次支付/取消） */
    private String groupNo;

    /** 按店铺拆分的独立订单（一店一单） */
    private java.util.List<OrderBrief> orders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBrief {
        private String orderNo;
        private Long shopId;
        private String shopName;
        private BigDecimal payAmount;
    }
}
