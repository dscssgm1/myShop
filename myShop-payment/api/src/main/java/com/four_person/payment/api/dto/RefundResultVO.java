package com.four_person.payment.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundResultVO {

    /** 退款操作是否成功（无支付记录也视为成功，只是无需退款） */
    private Boolean success;

    /** 是否真的发生了退款 */
    private Boolean refunded;

    private String paymentNo;

    private BigDecimal amount;

    /** 退款后的支付单状态 */
    private String status;

    private String message;

    public static RefundResultVO failure(String message) {
        RefundResultVO vo = new RefundResultVO();
        vo.setSuccess(false);
        vo.setRefunded(false);
        vo.setMessage(message);
        return vo;
    }
}
