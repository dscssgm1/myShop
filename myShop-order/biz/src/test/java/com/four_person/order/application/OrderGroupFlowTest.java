package com.four_person.order.application;

import com.four_person.common.api.Result;
import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.logistics.api.LogisticsInternalApi;
import com.four_person.merchant.api.MerchantInternalApi;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderGroup;
import com.four_person.order.domain.repository.OrderGroupRepository;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.order.infrastructure.support.OrderNoGenerator;
import com.four_person.order.infrastructure.support.PointsRuleReader;
import com.four_person.payment.api.PaymentInternalApi;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.StockChangeRequest;
import com.four_person.system.api.MemberInternalApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨店合单订单组（父订单）白盒测试：整组支付回调、取消黄金不变量。
 *
 * <p>黄金不变量：只要组内还有一个子订单在享受券优惠，券就不能释放；
 * 整组未支付取消 → 券全额退回；部分已付 → 只取消未付子单且券不退回；
 * 已付子单退款按分摊金额部分退款，整组关闭后券才退回。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderGroupFlowTest {

    private static final long USER = 900201L;
    private static final String GROUP_NO = "MG-TEST-0001";
    private static final String ORDER_A = "MS-TEST-000A";
    private static final String ORDER_B = "MS-TEST-000B";

    @Mock private OrderRepository orderRepository;
    @Mock private ProductInternalApi productInternalApi;
    @Mock private DiscountInternalApi discountInternalApi;
    @Mock private OrderNoGenerator orderNoGenerator;
    @Mock private MessageInternalApi messageInternalApi;
    @Mock private PaymentInternalApi paymentInternalApi;
    @Mock private MerchantInternalApi merchantInternalApi;
    @Mock private LogisticsInternalApi logisticsInternalApi;
    @Mock private MemberInternalApi memberInternalApi;
    @Mock private CommissionApplicationService commissionApplicationService;
    @Mock private OrderGroupRepository orderGroupRepository;
    @Mock private OrderGroupService orderGroupService;

    private OrderApplicationService service() {
        return new OrderApplicationService(orderRepository, productInternalApi, discountInternalApi,
                orderNoGenerator, messageInternalApi, paymentInternalApi, merchantInternalApi,
                logisticsInternalApi, memberInternalApi, commissionApplicationService,
                orderGroupRepository, orderGroupService,
                // jdbcTemplate 传 null → 配置读取兜底返回 0，抵现关闭，不干扰既有用例
                new PointsRuleReader(null));
    }

    // ==================== 测试数据构造 ====================

    private OrderGroup group(String status) {
        OrderGroup group = new OrderGroup();
        group.setGroupNo(GROUP_NO);
        group.setUserId(USER);
        group.setStatus(status);
        group.setShopCount(2);
        group.setPayAmount(new BigDecimal("80.00"));
        group.setCouponUseOrderNo(ORDER_A);
        return group;
    }

    private Order child(String orderNo, String status, BigDecimal payAmount) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(USER);
        order.setStatus(status);
        order.setBizType("NORMAL");
        order.setGroupNo(GROUP_NO);
        order.setShopId(ORDER_A.equals(orderNo) ? 900101L : 900102L);
        order.setPayAmount(payAmount);
        return order;
    }

    private RefundResultVO refundSuccess() {
        RefundResultVO vo = new RefundResultVO();
        vo.setSuccess(true);
        vo.setRefunded(true);
        return vo;
    }

    private void stubStockRestore() {
        lenient().when(productInternalApi.restoreStock(any(StockChangeRequest.class)))
                .thenReturn(Result.success(null));
    }

    // ==================== 合单支付 ====================

    @Test
    void markPaidGroupTransitionsAllChildrenAndGroup() {
        OrderGroup unpaid = group(OrderStatus.UNPAID.name());
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(unpaid);
        when(orderRepository.markGroupChildrenPaid(eq(GROUP_NO), any(LocalDateTime.class))).thenReturn(2);
        when(orderGroupRepository.markPaid(eq(GROUP_NO), any(LocalDateTime.class))).thenReturn(true);

        PaidRequest request = new PaidRequest();
        request.setPaymentNo("PAY-1");
        service().markPaidGroup(GROUP_NO, request);

        verify(orderRepository).markGroupChildrenPaid(eq(GROUP_NO), any(LocalDateTime.class));
        verify(orderGroupRepository).markPaid(eq(GROUP_NO), any(LocalDateTime.class));
        // 整组只推一条支付成功通知，不按子单刷屏
        verify(messageInternalApi, times(1)).pushNotice(any());
    }

    @Test
    void markPaidGroupIgnoresDuplicateCallback() {
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));

        service().markPaidGroup(GROUP_NO, new PaidRequest());

        verify(orderRepository, never()).markGroupChildrenPaid(anyString(), any());
        verify(orderGroupRepository, never()).markPaid(anyString(), any());
        verify(messageInternalApi, never()).pushNotice(any());
    }

    @Test
    void markPaidGroupFailsWhenNoChildTransitioned() {
        // 两次读取（首次 + 条件更新 0 行后复核）都返回未支付 → 状态异常
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.UNPAID.name()));
        when(orderRepository.markGroupChildrenPaid(eq(GROUP_NO), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service().markPaidGroup(GROUP_NO, new PaidRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(OrderErrorCode.ORDER_GROUP_STATUS_INVALID.getCode());
        verify(orderGroupRepository, never()).markPaid(anyString(), any());
    }

    // ==================== 整组取消：全部未支付 ====================

    @Test
    void cancelGroupAllUnpaidCancelsChildrenAndReleasesCouponsOnce() {
        OrderGroup unpaid = group(OrderStatus.UNPAID.name());
        Order childA = child(ORDER_A, OrderStatus.UNPAID.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.UNPAID.name(), new BigDecimal("53.33"));
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(unpaid);
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));
        when(orderRepository.markCancelled(eq(ORDER_A), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.markCancelled(eq(ORDER_B), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(ORDER_A)).thenReturn(List.of());
        when(orderRepository.findItems(ORDER_B)).thenReturn(List.of());
        stubStockRestore();
        when(orderGroupRepository.markCancelled(eq(GROUP_NO), any(LocalDateTime.class))).thenReturn(true);

        service().cancelGroup(USER, GROUP_NO);

        // 逐单取消 + 库存聚合归还一次 + 券按组一次性释放 + 组置为已取消
        verify(orderRepository).markCancelled(eq(ORDER_A), any(LocalDateTime.class));
        verify(orderRepository).markCancelled(eq(ORDER_B), any(LocalDateTime.class));
        verify(productInternalApi, times(1)).restoreStock(any(StockChangeRequest.class));
        verify(orderGroupService, times(1)).releaseCoupons(unpaid, List.of(childA, childB));
        verify(orderGroupRepository).markCancelled(eq(GROUP_NO), any(LocalDateTime.class));
    }

    @Test
    void cancelUnpaidChildEscalatesToWholeGroupCancel() {
        // 组内全部未付：用户点单个子单的取消 = 整组取消（未支付取消以组为单位）
        OrderGroup unpaid = group(OrderStatus.UNPAID.name());
        Order childA = child(ORDER_A, OrderStatus.UNPAID.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.UNPAID.name(), new BigDecimal("53.33"));
        when(orderRepository.findByOrderNo(ORDER_A)).thenReturn(Optional.of(childA));
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(unpaid);
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));
        when(orderRepository.markCancelled(eq(ORDER_A), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.markCancelled(eq(ORDER_B), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(anyString())).thenReturn(List.of());
        stubStockRestore();
        when(orderGroupRepository.markCancelled(eq(GROUP_NO), any(LocalDateTime.class))).thenReturn(true);

        service().cancel(USER, ORDER_A);

        verify(orderRepository).markCancelled(eq(ORDER_B), any(LocalDateTime.class));
        verify(orderGroupService).releaseCoupons(unpaid, List.of(childA, childB));
    }

    @Test
    void cancelGroupRejectsWhenAnyChildShipped() {
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));
        Order childA = child(ORDER_A, OrderStatus.SHIPPED.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.PAID.name(), new BigDecimal("53.33"));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));

        assertThatThrownBy(() -> service().cancelGroup(USER, GROUP_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(OrderErrorCode.ORDER_GROUP_STATUS_INVALID.getCode());
        verify(orderRepository, never()).markCancelled(anyString(), any());
    }

    // ==================== 整组取消：部分已支付（黄金不变量） ====================

    @Test
    void cancelGroupMixedCancelsUnpaidOnlyWithoutCouponRelease() {
        // 子单 A 已支付、子单 B 未支付：只取消 B，券不退回（防薅两次）
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));
        Order childA = child(ORDER_A, OrderStatus.PAID.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.UNPAID.name(), new BigDecimal("53.33"));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));
        when(orderRepository.markCancelled(eq(ORDER_B), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(ORDER_B)).thenReturn(List.of());
        stubStockRestore();

        service().cancelGroup(USER, GROUP_NO);

        verify(orderRepository).markCancelled(eq(ORDER_B), any(LocalDateTime.class));
        verify(orderRepository, never()).markCancelled(eq(ORDER_A), any());
        // 黄金不变量：券绝不释放
        verify(orderGroupService, never()).releaseCoupons(any(), any());
        verify(orderGroupRepository, never()).markCancelled(anyString(), any());
        verify(orderGroupRepository, never()).markClosed(anyString(), any());
    }

    @Test
    void cancelUnpaidChildInMixedGroupCancelsSelfOnlyAndKeepsCoupon() {
        Order childA = child(ORDER_A, OrderStatus.PAID.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.UNPAID.name(), new BigDecimal("53.33"));
        when(orderRepository.findByOrderNo(ORDER_B)).thenReturn(Optional.of(childB));
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));
        when(orderRepository.markCancelled(eq(ORDER_B), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(ORDER_B)).thenReturn(List.of());
        stubStockRestore();

        service().cancel(USER, ORDER_B);

        verify(orderRepository).markCancelled(eq(ORDER_B), any(LocalDateTime.class));
        verify(orderRepository, never()).markCancelled(eq(ORDER_A), any());
        verify(orderGroupService, never()).releaseCoupons(any(), any());
        verify(orderGroupRepository, never()).markCancelled(anyString(), any());
    }

    // ==================== 已付子单退款（黄金不变量） ====================

    @Test
    void cancelPaidGroupChildRefundsAllocatedShareOnly() {
        Order childA = child(ORDER_A, OrderStatus.PAID.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.PAID.name(), new BigDecimal("53.33"));
        when(orderRepository.findByOrderNo(ORDER_A)).thenReturn(Optional.of(childA));
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));
        when(paymentInternalApi.refund(any(RefundRequest.class))).thenReturn(Result.success(refundSuccess()));
        when(orderRepository.markRefundedAfterCancel(eq(ORDER_A), any(LocalDateTime.class))).thenReturn(true);

        service().cancel(USER, ORDER_A);

        // 合单共用一张支付单：按子单分摊金额部分退款，带组号
        ArgumentCaptor<RefundRequest> refundCaptor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentInternalApi).refund(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getGroupNo()).isEqualTo(GROUP_NO);
        assertThat(refundCaptor.getValue().getAmount().doubleValue()).isEqualTo(26.67);
        // 兄弟子单仍在享受优惠 → 检查关组但不释放（此处未全部关闭，真实逻辑在 OrderGroupService 单测）
        verify(orderGroupService).releaseCouponsIfGroupFullyClosed(childA);
        verify(productInternalApi, never()).restoreStock(any());
    }

    @Test
    void cancelPaidGroupChildRefundFailureBlocksEverything() {
        Order childA = child(ORDER_A, OrderStatus.PAID.name(), new BigDecimal("26.67"));
        Order childB = child(ORDER_B, OrderStatus.PAID.name(), new BigDecimal("53.33"));
        when(orderRepository.findByOrderNo(ORDER_A)).thenReturn(Optional.of(childA));
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));
        when(orderRepository.findByGroupNo(GROUP_NO)).thenReturn(List.of(childA, childB));
        RefundResultVO failure = new RefundResultVO();
        failure.setSuccess(false);
        when(paymentInternalApi.refund(any(RefundRequest.class))).thenReturn(Result.success(failure));

        assertThatThrownBy(() -> service().cancel(USER, ORDER_A))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(OrderErrorCode.REFUND_FAILED.getCode());
        verify(orderRepository, never()).markRefundedAfterCancel(anyString(), any());
        verify(orderGroupService, never()).releaseCouponsIfGroupFullyClosed(any());
    }

    // ==================== 组查询 ====================

    @Test
    void groupDetailRejectsOtherUsers() {
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.PAID.name()));

        assertThatThrownBy(() -> service().groupDetail(999L, GROUP_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(OrderErrorCode.ORDER_FORBIDDEN.getCode());
    }

    @Test
    void getGroupInternalExposesPayFields() {
        when(orderGroupService.requireGroup(GROUP_NO)).thenReturn(group(OrderStatus.UNPAID.name()));

        var dto = service().getGroupInternal(GROUP_NO);
        assertThat(dto.getGroupNo()).isEqualTo(GROUP_NO);
        assertThat(dto.getUserId()).isEqualTo(USER);
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.UNPAID.name());
        assertThat(dto.getPayAmount().doubleValue()).isEqualTo(80.00);
        assertThat(dto.getCouponUseOrderNo()).isEqualTo(ORDER_A);
    }
}
