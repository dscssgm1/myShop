package com.four_person.payment.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.payment.api.dto.CreatePaymentRequest;
import com.four_person.payment.api.dto.PaymentVO;
import com.four_person.payment.application.PaymentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping
    public Result<PaymentVO> create(@Valid @RequestBody CreatePaymentRequest request) {
        return Result.success(paymentApplicationService.create(UserContext.getUserId(), request));
    }

    @GetMapping("/{paymentNo}")
    public Result<PaymentVO> get(@PathVariable String paymentNo) {
        return Result.success(paymentApplicationService.get(UserContext.getUserId(), paymentNo));
    }

    @GetMapping("/order/{orderNo}")
    public Result<PaymentVO> getByOrder(@PathVariable String orderNo) {
        return Result.success(paymentApplicationService.getByOrder(UserContext.getUserId(), orderNo));
    }

    @PostMapping("/{paymentNo}/pay")
    public Result<PaymentVO> pay(@PathVariable String paymentNo) {
        return Result.success(paymentApplicationService.pay(UserContext.getUserId(), paymentNo));
    }

    @PostMapping("/{paymentNo}/close")
    public Result<Void> close(@PathVariable String paymentNo) {
        paymentApplicationService.close(UserContext.getUserId(), paymentNo);
        return Result.success();
    }
}
