package com.four_person.payment.api;

import com.four_person.common.api.Result;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 支付服务内部契约，供订单服务退款使用。
 */
@FeignClient(name = "myshop-payment", url = "${myshop.clients.payment-url:}")
public interface PaymentInternalApi {

    @PostMapping("/internal/payments/refund")
    Result<RefundResultVO> refund(@RequestBody RefundRequest request);

    @GetMapping("/internal/payments/orders/{orderNo}/status")
    Result<String> paymentStatus(@PathVariable("orderNo") String orderNo);
}
