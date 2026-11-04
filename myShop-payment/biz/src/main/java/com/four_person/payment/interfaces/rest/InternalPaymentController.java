package com.four_person.payment.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.payment.application.PaymentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供订单服务调用的内部接口。
 */
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping("/refund")
    public Result<RefundResultVO> refund(@Valid @RequestBody RefundRequest request) {
        return Result.success(paymentApplicationService.refund(request));
    }

    @GetMapping("/orders/{orderNo}/status")
    public Result<String> status(@PathVariable String orderNo) {
        return Result.success(paymentApplicationService.paymentStatusByOrderNo(orderNo));
    }
}
