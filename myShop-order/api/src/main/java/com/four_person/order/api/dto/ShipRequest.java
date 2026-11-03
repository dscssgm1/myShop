package com.four_person.order.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 商家发货请求：选择物流公司，由订单服务编排创建运单 */
@Data
public class ShipRequest {

    @NotNull(message = "请选择物流公司")
    private Long companyId;
}
