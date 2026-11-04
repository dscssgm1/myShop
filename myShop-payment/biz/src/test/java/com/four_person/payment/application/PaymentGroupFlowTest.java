package com.four_person.payment.application;

import com.four_person.common.api.Result;
import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.OrderInternalApi;
import com.four_person.order.api.dto.OrderGroupInternalDTO;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.payment.api.dto.CreatePaymentRequest;
import com.four_person.payment.api.dto.PaymentVO;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.payment.domain.model.Payment;
import com.four_person.payment.domain.repository.PaymentRepository;
import com.four_person.payment.enums.PaymentErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合单支付白盒测试：跨店订单组一次付清（支付成功整组回调）、
 * 子单退款按分摊金额部分退款（退满自动整单 REFUNDED）。
 */
@ExtendWith(MockitoExtension.class)
class PaymentGroupFlowTest {

    private static final long USER = 900201L;
    private static final String GROUP_NO = "MG-TEST-0001";
    private static final String ORDER_A = "MS-TEST-000A";
    private static final String PAYMENT_NO = "PAY-TEST-0001";

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderInternalApi orderInternalApi;

    private PaymentApplicationService service() {
        return new PaymentApplicationService(paymentRepository, orderInternalApi);
    }

    // ==================== 测试数据构造 ====================

    private OrderGroupInternalDTO groupDTO(String status) {
        OrderGroupInternalDTO dto = new OrderGroupInternalDTO();
        dto.setGroupNo(GROUP_NO);
        dto.setUserId(USER);
        dto.setStatus(status);
        dto.setShopCount(2);
        dto.setPayAmount(new BigDecimal("80.00"));
        dto.setCouponUseOrderNo(ORDER_A);
        return dto;
    }

    private OrderInternalDTO orderDTO(String status, String groupNo) {
        OrderInternalDTO dto = new OrderInternalDTO();
        dto.setOrderNo(ORDER_A);
        dto.setUserId(USER);
        dto.setStatus(status);
        dto.setGroupNo(groupNo);
        dto.setPayAmount(new BigDecimal("26.67"));
        return dto;
    }

    private Payment pendingGroupPayment() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo(PAYMENT_NO);
        payment.setOrderNo(ORDER_A);
        payment.setGroupNo(GROUP_NO);
        payment.setUserId(USER);
        payment.setAmount(new BigDecimal("80.00"));
        payment.setChannel("ALIPAY");
        payment.setStatus("PENDING");
        payment.setRefundedAmount(BigDecimal.ZERO);
        return payment;
    }

    private Payment successGroupPayment() {
        Payment payment = pendingGroupPayment();
        payment.setStatus("SUCCESS");
        payment.setPaidAt(LocalDateTime.now());
        return payment;
    }

    // ==================== 创建合单支付单 ====================

    @Test
    void createGroupPaymentStoresGroupNoAndTotalAmount() {
        when(orderInternalApi.getOrderGroup(GROUP_NO)).thenReturn(Result.success(groupDTO("UNPAID")));
        when(paymentRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setGroupNo(GROUP_NO);
        request.setChannel("ALIPAY");
        PaymentVO vo = service().create(USER, request);

        assertThat(vo.getGroupNo()).isEqualTo(GROUP_NO);
        assertThat(vo.getAmount().doubleValue()).isEqualTo(80.00);
        // order_no 记录组的核销单号（第一张子单）
        assertThat(vo.getOrderNo()).isEqualTo(ORDER_A);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupNo()).isEqualTo(GROUP_NO);
        assertThat(captor.getValue().getRefundedAmount()).isNull();
    }

    @Test
    void createRejectsAmbiguousOrderAndGroupParams() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo(ORDER_A);
        request.setGroupNo(GROUP_NO);
        request.setChannel("ALIPAY");

        assertThatThrownBy(() -> service().create(USER, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(PaymentErrorCode.PAY_PARAM_INVALID.getCode());
    }

    @Test
    void createRejectsSinglePaymentForGroupChildOrder() {
        // 合单子订单不允许单独支付，必须整组一次付清
        when(orderInternalApi.getOrder(ORDER_A)).thenReturn(Result.success(orderDTO("UNPAID", GROUP_NO)));
        when(orderInternalApi.hasUnpaidRecovery(USER)).thenReturn(Result.success(false));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderNo(ORDER_A);
        request.setChannel("ALIPAY");
        assertThatThrownBy(() -> service().create(USER, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(PaymentErrorCode.ORDER_NOT_PAYABLE.getCode());
    }

    @Test
    void createGroupPaymentRejectsPaidGroup() {
        when(orderInternalApi.getOrderGroup(GROUP_NO)).thenReturn(Result.success(groupDTO("PAID")));

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setGroupNo(GROUP_NO);
        request.setChannel("ALIPAY");
        assertThatThrownBy(() -> service().create(USER, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(PaymentErrorCode.GROUP_NOT_PAYABLE.getCode());
    }

    // ==================== 合单支付回调 ====================

    @Test
    void payGroupCallsGroupPaidCallbackInsteadOfSingleOrderPaid() {
        Payment payment = pendingGroupPayment();
        when(paymentRepository.findByPaymentNo(PAYMENT_NO)).thenReturn(Optional.of(payment));
        when(paymentRepository.markPaid(eq(PAYMENT_NO), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(orderInternalApi.groupPaid(eq(GROUP_NO), any(PaidRequest.class))).thenReturn(Result.success(null));

        PaymentVO vo = service().pay(USER, PAYMENT_NO);

        assertThat(vo.getStatus()).isEqualTo("SUCCESS");
        verify(orderInternalApi).groupPaid(eq(GROUP_NO), any(PaidRequest.class));
        verify(orderInternalApi, never()).paid(anyString(), any());
    }

    @Test
    void paySingleOrderKeepsLegacyCallback() {
        Payment payment = pendingGroupPayment();
        payment.setGroupNo(null);
        when(paymentRepository.findByPaymentNo(PAYMENT_NO)).thenReturn(Optional.of(payment));
        when(paymentRepository.markPaid(eq(PAYMENT_NO), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(orderInternalApi.paid(eq(ORDER_A), any(PaidRequest.class))).thenReturn(Result.success(null));

        service().pay(USER, PAYMENT_NO);

        verify(orderInternalApi).paid(eq(ORDER_A), any(PaidRequest.class));
        verify(orderInternalApi, never()).groupPaid(anyString(), any());
    }

    // ==================== 部分退款 ====================

    @Test
    void refundChildOrderPartiallyRefundsAllocatedShare() {
        Payment payment = successGroupPayment();
        when(paymentRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(payment));
        when(paymentRepository.markPartialRefund(eq(PAYMENT_NO),
                eq(new BigDecimal("26.67")), any(LocalDateTime.class), anyString())).thenReturn(true);

        RefundRequest request = new RefundRequest();
        request.setOrderNo(ORDER_A);
        request.setGroupNo(GROUP_NO);
        request.setAmount(new BigDecimal("26.67"));
        request.setReason("用户取消订单退款");
        RefundResultVO result = service().refund(request);

        // 退款后仍有余额 → 支付单保持 SUCCESS
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getRefunded()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getAmount().doubleValue()).isEqualTo(26.67);
        verify(paymentRepository).markPartialRefund(eq(PAYMENT_NO),
                eq(new BigDecimal("26.67")), any(LocalDateTime.class), anyString());
        verify(paymentRepository, never()).markRefunded(anyString(), any(), anyString());
    }

    @Test
    void refundLastChildClosesWholeGroupPayment() {
        // 组支付单 80 元，最后一个子单退 53.33 → 剩余可退余额不足全额？此处按剩余 53.33 恰好退满
        Payment payment = successGroupPayment();
        payment.setRefundedAmount(new BigDecimal("26.67"));
        when(paymentRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(payment));
        when(paymentRepository.markPartialRefund(eq(PAYMENT_NO),
                eq(new BigDecimal("53.33")), any(LocalDateTime.class), anyString())).thenReturn(true);

        RefundRequest request = new RefundRequest();
        request.setOrderNo(ORDER_A);
        request.setGroupNo(GROUP_NO);
        request.setAmount(new BigDecimal("53.33"));
        RefundResultVO result = service().refund(request);

        assertThat(result.getSuccess()).isTrue();
        verify(paymentRepository).markPartialRefund(eq(PAYMENT_NO),
                eq(new BigDecimal("53.33")), any(LocalDateTime.class), anyString());
    }

    @Test
    void refundWithoutAmountSettlesWholePayment() {
        Payment payment = successGroupPayment();
        when(paymentRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(payment));
        when(paymentRepository.markRefunded(eq(PAYMENT_NO), any(LocalDateTime.class), anyString()))
                .thenReturn(true);

        RefundRequest request = new RefundRequest();
        request.setOrderNo(ORDER_A);
        request.setGroupNo(GROUP_NO);
        RefundResultVO result = service().refund(request);

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(paymentRepository).markRefunded(eq(PAYMENT_NO), any(LocalDateTime.class), anyString());
        verify(paymentRepository, never()).markPartialRefund(anyString(), any(), any(), anyString());
    }

    @Test
    void refundResolvesByOrderNoWhenGroupNoAbsent() {
        Payment payment = successGroupPayment();
        payment.setGroupNo(null);
        when(paymentRepository.findByOrderNo(ORDER_A)).thenReturn(Optional.of(payment));
        when(paymentRepository.markRefunded(eq(PAYMENT_NO), any(LocalDateTime.class), anyString()))
                .thenReturn(true);

        RefundRequest request = new RefundRequest();
        request.setOrderNo(ORDER_A);
        service().refund(request);

        verify(paymentRepository).findByOrderNo(ORDER_A);
        verify(paymentRepository, times(1)).markRefunded(anyString(), any(), anyString());
    }

    @Test
    void refundMissingPaymentIsSuccessWithoutRefund() {
        when(paymentRepository.findByOrderNo(ORDER_A)).thenReturn(Optional.empty());

        RefundRequest request = new RefundRequest();
        request.setOrderNo(ORDER_A);
        RefundResultVO result = service().refund(request);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getRefunded()).isFalse();
        verify(paymentRepository, never()).markRefunded(anyString(), any(), anyString());
    }

    @Test
    void refundAlreadyRefundedPaymentIsIdempotent() {
        Payment payment = successGroupPayment();
        payment.setStatus("REFUNDED");
        when(paymentRepository.findByGroupNo(GROUP_NO)).thenReturn(Optional.of(payment));

        RefundRequest request = new RefundRequest();
        request.setOrderNo(ORDER_A);
        request.setGroupNo(GROUP_NO);
        request.setAmount(new BigDecimal("26.67"));
        RefundResultVO result = service().refund(request);

        assertThat(result.getRefunded()).isTrue();
        assertThat(result.getMessage()).contains("已退款");
        verify(paymentRepository, never()).markPartialRefund(anyString(), any(), any(), anyString());
    }
}
