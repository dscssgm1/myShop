package com.four_person.order.application;

import com.four_person.common.api.Result;
import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.logistics.api.LogisticsInternalApi;
import com.four_person.merchant.api.MerchantInternalApi;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.order.api.dto.CreateOrderVO;
import com.four_person.order.api.dto.OrderCreateRequest;
import com.four_person.order.api.dto.OrderItemRequest;
import com.four_person.order.api.dto.OrderPreviewItemVO;
import com.four_person.order.api.dto.OrderPreviewRequest;
import com.four_person.order.api.dto.OrderPreviewVO;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderCoupon;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.repository.OrderGroupRepository;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.order.infrastructure.support.OrderNoGenerator;
import com.four_person.order.infrastructure.support.PointsRuleReader;
import com.four_person.payment.api.PaymentInternalApi;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductVO;
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
 * 下单优惠券链路白盒测试：多店铺分摊尾差、券核销/回滚、取消订单释放。
 * 跨服务边界（商品/折扣/支付）用 Mockito 桩，订单仓储落库行为用捕获器断言。
 */
@ExtendWith(MockitoExtension.class)
class OrderCouponFlowTest {

    private static final long USER = 900201L;
    private static final long SHOP_A = 900101L;
    private static final long SHOP_B = 900102L;
    private static final String ORDER_NO = "MS-TEST-0001";

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

    /** 在售商品（无 SKU，单价即商品价） */
    private ProductVO product(long id, long shopId, String shopName, long categoryId, String price, int stock) {
        ProductVO product = new ProductVO();
        product.setId(id);
        product.setName("商品" + id);
        product.setStatus(1);
        product.setShopId(shopId);
        product.setShopName(shopName);
        product.setCategoryId(categoryId);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }

    private DiscountResultVO discount(String pay, String promotion, String coupon,
                                      String merchantBear, String platformBear,
                                      DiscountResultVO.CouponUsage... usages) {
        DiscountResultVO vo = new DiscountResultVO();
        vo.setPayAmount(new BigDecimal(pay));
        vo.setPromotionDiscount(new BigDecimal(promotion));
        vo.setCouponDiscount(new BigDecimal(coupon));
        vo.setTotalDiscount(new BigDecimal(promotion).add(new BigDecimal(coupon)));
        vo.setMerchantBearAmount(new BigDecimal(merchantBear));
        vo.setPlatformBearAmount(new BigDecimal(platformBear));
        vo.setAppliedCoupons(List.of(usages));
        vo.setAppliedCouponId(usages.length > 0 ? usages[0].getCouponId() : null);
        return vo;
    }

    private DiscountResultVO.CouponUsage usage(long couponId, String title, String amount) {
        DiscountResultVO.CouponUsage usage = new DiscountResultVO.CouponUsage();
        usage.setCouponId(couponId);
        usage.setTitle(title);
        usage.setDiscount(new BigDecimal(amount));
        return usage;
    }

    private OrderCreateRequest createRequest(OrderItemRequest... items) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setItems(List.of(items));
        request.setReceiverName("测试收件人");
        request.setReceiverPhone("13800000000");
        request.setReceiverAddress("测试地址 1 号");
        return request;
    }

    private OrderItemRequest item(long productId, int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private OrderItem orderItem(long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private void stubPrepare(List<ProductVO> products, DiscountResultVO discountVO) {
        lenient().when(orderRepository.hasUnpaidRecovery(USER)).thenReturn(false);
        lenient().when(productInternalApi.batch(any())).thenReturn(Result.success(products));
        lenient().when(discountInternalApi.calculate(any(DiscountCalculateRequest.class)))
                .thenReturn(Result.success(discountVO));
        lenient().when(orderNoGenerator.next()).thenReturn(ORDER_NO);
    }

    private void stubStockApis() {
        lenient().when(productInternalApi.deductStock(any(StockChangeRequest.class)))
                .thenReturn(Result.success(null));
        lenient().when(productInternalApi.restoreStock(any(StockChangeRequest.class)))
                .thenReturn(Result.success(null));
    }

    // ==================== 多店铺分摊 ====================

    @Test
    void previewAllocatesByShopRatioWithRemainderToLastShop() {
        // 两店小计 33.33 / 66.66（比例约 1:2），整体应付 80、活动优惠 10、商家承担 6、平台承担 4
        stubPrepare(List.of(
                        product(1L, SHOP_A, "店铺A", 7L, "33.33", 10),
                        product(2L, SHOP_B, "店铺B", 8L, "66.66", 10)),
                discount("80.00", "10.00", "0.00", "6.00", "4.00"));

        OrderPreviewRequest request = new OrderPreviewRequest();
        request.setItems(List.of(item(1L, 1), item(2L, 1)));
        OrderPreviewVO vo = service().preview(USER, request);

        assertThat(vo.getOriginalAmount().doubleValue()).isEqualTo(99.99);
        assertThat(vo.getShops()).hasSize(2);
        OrderPreviewVO.ShopPreviewVO shopA = vo.getShops().get(0);
        OrderPreviewVO.ShopPreviewVO shopB = vo.getShops().get(1);
        // A 店按比例 26.67，B 店吃尾差 53.33，合计恰为 80
        assertThat(shopA.getPayAmount().doubleValue()).isEqualTo(26.67);
        assertThat(shopB.getPayAmount().doubleValue()).isEqualTo(53.33);
        assertThat(shopA.getPromotionDiscount().doubleValue()).isEqualTo(3.33);
        assertThat(shopB.getPromotionDiscount().doubleValue()).isEqualTo(6.67);
        assertThat(shopA.getCouponDiscount().doubleValue()).isEqualTo(3.33);
        assertThat(shopB.getCouponDiscount().doubleValue()).isEqualTo(6.66);
    }

    @Test
    void createPersistsPerShopBearAllocation() {
        stubPrepare(List.of(
                        product(1L, SHOP_A, "店铺A", 7L, "33.33", 10),
                        product(2L, SHOP_B, "店铺B", 8L, "66.66", 10)),
                discount("80.00", "10.00", "0.00", "6.00", "4.00"));
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderNoGenerator.nextGroup()).thenReturn("MG-TEST-0001");

        CreateOrderVO vo = service().create(USER, createRequest(item(1L, 1), item(2L, 1)));
        assertThat(vo.getOrders()).hasSize(2);
        // 跨店结算生成订单组，子单都挂组号
        assertThat(vo.getGroupNo()).isEqualTo("MG-TEST-0001");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(captor.capture());
        Order orderA = captor.getAllValues().get(0);
        Order orderB = captor.getAllValues().get(1);
        assertThat(orderA.getGroupNo()).isEqualTo("MG-TEST-0001");
        assertThat(orderB.getGroupNo()).isEqualTo("MG-TEST-0001");
        // 承担金额随优惠一起按店铺比例拆分，两单合计守恒
        assertThat(orderA.getMerchantBearAmount().doubleValue()).isEqualTo(2.00);
        assertThat(orderA.getPlatformBearAmount().doubleValue()).isEqualTo(1.33);
        assertThat(orderB.getMerchantBearAmount().doubleValue()).isEqualTo(4.00);
        assertThat(orderB.getPlatformBearAmount().doubleValue()).isEqualTo(2.67);
        assertThat(orderA.getPayAmount().add(orderB.getPayAmount()).doubleValue()).isEqualTo(80.00);
        // 未用券：券字段为空，不落核销明细
        assertThat(orderA.getCouponId()).isNull();
        verify(orderRepository, never()).saveCoupons(anyString(), any());

        // 父订单落库：金额取分摊前总额，核销单号取第一张子单
        org.mockito.ArgumentCaptor<com.four_person.order.domain.model.OrderGroup> groupCaptor =
                org.mockito.ArgumentCaptor.forClass(com.four_person.order.domain.model.OrderGroup.class);
        verify(orderGroupRepository).save(groupCaptor.capture());
        com.four_person.order.domain.model.OrderGroup group = groupCaptor.getValue();
        assertThat(group.getGroupNo()).isEqualTo("MG-TEST-0001");
        assertThat(group.getShopCount()).isEqualTo(2);
        assertThat(group.getOriginalAmount().doubleValue()).isEqualTo(99.99);
        assertThat(group.getPayAmount().doubleValue()).isEqualTo(80.00);
        assertThat(group.getPromotionDiscount().doubleValue()).isEqualTo(10.00);
        assertThat(group.getPayAmount()).isEqualTo(orderA.getPayAmount().add(orderB.getPayAmount()));
        assertThat(group.getCouponUseOrderNo()).isEqualTo(orderA.getOrderNo());
    }

    // ==================== 下单用券 ====================

    @Test
    void createSingleShopUsesCouponsAndPersistsRedemptionRows() {
        stubPrepare(List.of(product(1L, SHOP_A, "店铺A", 7L, "100.00", 10)),
                discount("70.00", "0.00", "30.00", "20.00", "10.00",
                        usage(10L, "满减券A", "20.00"), usage(11L, "立减券B", "10.00")));
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discountInternalApi.useCoupon(any(Long.class), any(CouponUseRequest.class)))
                .thenReturn(Result.success(null));

        service().create(USER, createRequest(item(1L, 1)));

        // 主券 = 与 appliedCouponId 匹配的那张（10），券名按核销顺序拼接
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order order = orderCaptor.getValue();
        assertThat(order.getCouponId()).isEqualTo(10L);
        assertThat(order.getCouponTitle()).isEqualTo("满减券A、立减券B");
        // 单店铺不生成订单组
        assertThat(order.getGroupNo()).isNull();
        verify(orderGroupRepository, never()).save(any());

        // 一单多券核销明细逐张落库
        ArgumentCaptor<List<OrderCoupon>> couponCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).saveCoupons(eq(ORDER_NO), couponCaptor.capture());
        assertThat(couponCaptor.getValue()).extracting(OrderCoupon::getCouponId)
                .containsExactly(10L, 11L);
        assertThat(couponCaptor.getValue()).extracting(OrderCoupon::getDiscount)
                .allSatisfy(amount -> assertThat(amount.doubleValue()).isPositive());

        // 每张券调用一次核销接口，userId/orderNo 正确
        ArgumentCaptor<CouponUseRequest> useCaptor = ArgumentCaptor.forClass(CouponUseRequest.class);
        verify(discountInternalApi, times(2)).useCoupon(any(Long.class), useCaptor.capture());
        assertThat(useCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.getUserId()).isEqualTo(USER);
            assertThat(request.getOrderNo()).isEqualTo(ORDER_NO);
        });
        verify(productInternalApi).deductStock(any(StockChangeRequest.class));
    }

    @Test
    void createMultiShopWithCouponsPersistsRowsPerOrder() {
        stubPrepare(List.of(
                        product(1L, SHOP_A, "店铺A", 7L, "50.00", 10),
                        product(2L, SHOP_B, "店铺B", 8L, "50.00", 10)),
                discount("80.00", "0.00", "20.00", "0.00", "20.00", usage(10L, "券", "20.00")));
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderNoGenerator.next()).thenReturn(ORDER_NO, "MS-TEST-0002");
        when(orderNoGenerator.nextGroup()).thenReturn("MG-TEST-0001");
        when(discountInternalApi.useCoupon(any(Long.class), any(CouponUseRequest.class)))
                .thenReturn(Result.success(null));

        CreateOrderVO vo = service().create(USER, createRequest(item(1L, 1), item(2L, 1)));

        // 跨店铺也允许用券：两张子订单都写入券核销明细
        assertThat(vo.getOrders()).hasSize(2);
        ArgumentCaptor<List<OrderCoupon>> couponCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository, times(2)).saveCoupons(anyString(), couponCaptor.capture());
        assertThat(couponCaptor.getAllValues()).allSatisfy(rows ->
                assertThat(rows).extracting(OrderCoupon::getCouponId).containsExactly(10L));

        // 核销挂在第一张子订单的订单号上；取消任一子订单按 orderNo 幂等释放一次
        ArgumentCaptor<CouponUseRequest> useCaptor = ArgumentCaptor.forClass(CouponUseRequest.class);
        verify(discountInternalApi, times(1)).useCoupon(eq(10L), useCaptor.capture());
        assertThat(useCaptor.getValue().getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(vo.getGroupNo()).isEqualTo("MG-TEST-0001");
    }

    @Test
    void createRollsBackUsedCouponsWhenRedemptionFails() {
        stubPrepare(List.of(product(1L, SHOP_A, "店铺A", 7L, "100.00", 10)),
                discount("70.00", "0.00", "30.00", "0.00", "30.00",
                        usage(10L, "券A", "20.00"), usage(11L, "券B", "10.00")));
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // 第一张核销成功，第二张失败 → 已核销的第一张必须释放
        when(discountInternalApi.useCoupon(eq(10L), any(CouponUseRequest.class)))
                .thenReturn(Result.success(null));
        when(discountInternalApi.useCoupon(eq(11L), any(CouponUseRequest.class)))
                .thenThrow(new RuntimeException("折扣服务不可用"));

        assertThatThrownBy(() -> service().create(USER, createRequest(item(1L, 1))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("折扣服务不可用");

        verify(discountInternalApi).releaseCoupon(eq(10L), any(CouponReleaseRequest.class));
        verify(discountInternalApi, never()).releaseCoupon(eq(11L), any());
        // 库存回滚
        verify(productInternalApi).restoreStock(any(StockChangeRequest.class));
    }

    @Test
    void createWithoutCouponsSkipsRedemptionEntirely() {
        stubPrepare(List.of(product(1L, SHOP_A, "店铺A", 7L, "100.00", 10)),
                discount("100.00", "0.00", "0.00", "0.00", "0.00"));
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(USER, createRequest(item(1L, 1)));
        verify(discountInternalApi, never()).useCoupon(any(), any());
        verify(orderRepository, never()).saveCoupons(anyString(), any());
    }

    // ==================== 结算页活动选择 / 逐商品优惠 ====================

    /** 引擎返回的行级优惠明细（原价小计 / 活动 / 券 / 优惠后） */
    private DiscountResultVO.ItemDiscount row(long productId, String original, String promo, String coupon) {
        DiscountResultVO.ItemDiscount row = new DiscountResultVO.ItemDiscount();
        row.setProductId(productId);
        row.setOriginalSubtotal(new BigDecimal(original));
        row.setPromotionDiscount(new BigDecimal(promo));
        row.setCouponDiscount(new BigDecimal(coupon));
        row.setFinalSubtotal(new BigDecimal(original).subtract(new BigDecimal(promo))
                .subtract(new BigDecimal(coupon)));
        row.setPromotions(List.of());
        row.setCoupons(List.of());
        return row;
    }

    private DiscountResultVO.PromotionOption option(long id, String title, String amount) {
        DiscountResultVO.PromotionOption option = new DiscountResultVO.PromotionOption();
        option.setPromotionId(id);
        option.setTitle(title);
        option.setShopId(0L);
        option.setRuleText("满 90 减 20");
        option.setScopeText("全品类");
        option.setDiscount(new BigDecimal(amount));
        option.setCouponStackable(true);
        option.setChosen(true);
        return option;
    }

    @Test
    void previewMapsPromotionOptionsAndPassesRequestedIds() {
        DiscountResultVO discountVO = discount("70.00", "20.00", "10.00", "0.00", "30.00");
        discountVO.setAppliedPromotionIds(List.of(7L));
        discountVO.setPromotionOptions(List.of(option(7L, "平台大促", "20.00")));
        discountVO.setItemDiscounts(List.of(row(1L, "100.00", "20.00", "10.00")));
        stubPrepare(List.of(product(1L, SHOP_A, "店铺A", 7L, "100.00", 10)), discountVO);

        OrderPreviewRequest request = new OrderPreviewRequest();
        request.setItems(List.of(item(1L, 1)));
        request.setPromotionIds(List.of(7L));
        OrderPreviewVO vo = service().preview(USER, request);

        // 用户所选活动原样透传给折扣服务
        ArgumentCaptor<DiscountCalculateRequest> calcCaptor =
                ArgumentCaptor.forClass(DiscountCalculateRequest.class);
        verify(discountInternalApi).calculate(calcCaptor.capture());
        assertThat(calcCaptor.getValue().getPromotionIds()).containsExactly(7L);

        // 活动选项与逐商品优惠明细映射到预览结果
        assertThat(vo.getAppliedPromotionIds()).containsExactly(7L);
        assertThat(vo.getPromotionOptions()).hasSize(1);
        OrderPreviewVO.PromotionOptionVO mapped = vo.getPromotionOptions().get(0);
        assertThat(mapped.getPromotionId()).isEqualTo(7L);
        assertThat(mapped.getDiscount().doubleValue()).isEqualTo(20.0);
        assertThat(mapped.getChosen()).isTrue();

        OrderPreviewItemVO item = vo.getItems().get(0);
        assertThat(item.getPromotionDiscount().doubleValue()).isEqualTo(20);
        assertThat(item.getCouponDiscount().doubleValue()).isEqualTo(10);
        assertThat(item.getFinalSubtotal().doubleValue()).isEqualTo(70);
    }

    @Test
    void previewWithoutPromotionSelectionSendsNullForAutoBest() {
        DiscountResultVO discountVO = discount("70.00", "20.00", "10.00", "0.00", "30.00");
        stubPrepare(List.of(product(1L, SHOP_A, "店铺A", 7L, "100.00", 10)), discountVO);

        OrderPreviewRequest request = new OrderPreviewRequest();
        request.setItems(List.of(item(1L, 1)));
        OrderPreviewVO vo = service().preview(USER, request);

        ArgumentCaptor<DiscountCalculateRequest> calcCaptor =
                ArgumentCaptor.forClass(DiscountCalculateRequest.class);
        verify(discountInternalApi).calculate(calcCaptor.capture());
        // 未指定 → null，由引擎自动选最优组合
        assertThat(calcCaptor.getValue().getPromotionIds()).isNull();
        // 旧返回无逐商品明细 → 行级字段兜底为零，不抛空指针
        OrderPreviewItemVO item = vo.getItems().get(0);
        assertThat(item.getPromotionDiscount().doubleValue()).isZero();
        assertThat(item.getCouponDiscount().doubleValue()).isZero();
        assertThat(item.getFinalSubtotal().doubleValue()).isEqualTo(100);
        assertThat(item.getItemPromotions()).isEmpty();
    }

    @Test
    void createPersistsPerItemDiscountsFromEngineBreakdown() {
        DiscountResultVO discountVO = discount("70.00", "20.00", "10.00", "0.00", "30.00",
                usage(10L, "券", "10.00"));
        discountVO.setItemDiscounts(List.of(row(1L, "100.00", "20.00", "10.00")));
        stubPrepare(List.of(product(1L, SHOP_A, "店铺A", 7L, "100.00", 10)), discountVO);
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(discountInternalApi.useCoupon(any(Long.class), any(CouponUseRequest.class)))
                .thenReturn(Result.success(null));

        service().create(USER, createRequest(item(1L, 1)));

        // 订单行落库带行级优惠快照
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).saveItems(itemsCaptor.capture());
        OrderItem saved = itemsCaptor.getValue().get(0);
        assertThat(saved.getPromotionDiscount().doubleValue()).isEqualTo(20);
        assertThat(saved.getCouponDiscount().doubleValue()).isEqualTo(10);

        // 店铺级金额取行级明细之和（与引擎总额一致）
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getPromotionDiscount().doubleValue()).isEqualTo(20);
        assertThat(orderCaptor.getValue().getCouponDiscount().doubleValue()).isEqualTo(10);
        assertThat(orderCaptor.getValue().getPayAmount().doubleValue()).isEqualTo(70);
    }

    @Test
    void crossShopAllocationFollowsEngineRowsNotProportionalSpread() {
        // 活动只命中 B 店：按比例分摊会给 A 店也摊一半；行级明细齐全时应精确落到命中店铺
        DiscountResultVO discountVO = discount("80.00", "10.00", "10.00", "0.00", "20.00");
        discountVO.setItemDiscounts(List.of(
                row(1L, "50.00", "0.00", "5.00"),
                row(2L, "50.00", "10.00", "5.00")));
        stubPrepare(List.of(
                        product(1L, SHOP_A, "店铺A", 7L, "50.00", 10),
                        product(2L, SHOP_B, "店铺B", 8L, "50.00", 10)), discountVO);
        stubStockApis();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderNoGenerator.next()).thenReturn(ORDER_NO, "MS-TEST-0002");
        when(orderNoGenerator.nextGroup()).thenReturn("MG-TEST-0001");

        service().create(USER, createRequest(item(1L, 1), item(2L, 1)));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(captor.capture());
        Order shopA = captor.getAllValues().get(0);
        Order shopB = captor.getAllValues().get(1);
        assertThat(shopA.getPromotionDiscount().doubleValue()).isZero();
        assertThat(shopA.getCouponDiscount().doubleValue()).isEqualTo(5);
        assertThat(shopA.getPayAmount().doubleValue()).isEqualTo(45);
        assertThat(shopB.getPromotionDiscount().doubleValue()).isEqualTo(10);
        assertThat(shopB.getCouponDiscount().doubleValue()).isEqualTo(5);
        assertThat(shopB.getPayAmount().doubleValue()).isEqualTo(35);
        // 承担额按各店活动优惠占比：活动全在 B 店 → 平台补贴也全给 B 店
        assertThat(shopA.getPlatformBearAmount().doubleValue()).isZero();
        assertThat(shopB.getPlatformBearAmount().doubleValue()).isEqualTo(20);
        // 子单应付合计仍与订单组总额守恒
        assertThat(shopA.getPayAmount().add(shopB.getPayAmount()).doubleValue()).isEqualTo(80);
    }

    // ==================== 取消订单释放 ====================

    private Order order(String status, Long couponId) {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER);
        order.setStatus(status);
        order.setBizType("NORMAL");
        order.setCouponId(couponId);
        return order;
    }

    @Test
    void cancelUnpaidReleasesEachRedeemedCouponOnce() {
        Order order = order(OrderStatus.UNPAID.name(), 10L);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.markCancelled(eq(ORDER_NO), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(ORDER_NO)).thenReturn(List.of(orderItem(1L, 1)));
        // 一单两券：10、11，distinct 后逐张释放
        OrderCoupon first = new OrderCoupon();
        first.setCouponId(10L);
        OrderCoupon second = new OrderCoupon();
        second.setCouponId(11L);
        when(orderRepository.findCoupons(ORDER_NO)).thenReturn(List.of(first, second, first));
        lenient().when(productInternalApi.restoreStock(any(StockChangeRequest.class)))
                .thenReturn(Result.success(null));
        lenient().when(discountInternalApi.releaseCoupon(any(Long.class), any(CouponReleaseRequest.class)))
                .thenReturn(Result.success(null));

        service().cancel(USER, ORDER_NO);

        verify(discountInternalApi).releaseCoupon(eq(10L), any(CouponReleaseRequest.class));
        verify(discountInternalApi).releaseCoupon(eq(11L), any(CouponReleaseRequest.class));
        verify(productInternalApi).restoreStock(any(StockChangeRequest.class));
    }

    @Test
    void cancelLegacyOrderFallsBackToPrimaryCoupon() {
        // 历史订单无核销明细 → 回退主券 couponId
        Order order = order(OrderStatus.UNPAID.name(), 10L);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.markCancelled(eq(ORDER_NO), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(ORDER_NO)).thenReturn(List.of());
        when(orderRepository.findCoupons(ORDER_NO)).thenReturn(List.of());
        lenient().when(productInternalApi.restoreStock(any(StockChangeRequest.class)))
                .thenReturn(Result.success(null));
        lenient().when(discountInternalApi.releaseCoupon(any(Long.class), any(CouponReleaseRequest.class)))
                .thenReturn(Result.success(null));

        service().cancel(USER, ORDER_NO);
        verify(discountInternalApi, times(1)).releaseCoupon(eq(10L), any(CouponReleaseRequest.class));
    }

    @Test
    void cancelPaidRefundFailureBlocksCouponRelease() {
        Order order = order(OrderStatus.PAID.name(), 10L);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(java.util.Optional.of(order));
        RefundResultVO failure = new RefundResultVO();
        failure.setSuccess(false);
        when(paymentInternalApi.refund(any(RefundRequest.class))).thenReturn(Result.success(failure));

        assertThatThrownBy(() -> service().cancel(USER, ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(OrderErrorCode.REFUND_FAILED.getCode());
        verify(discountInternalApi, never()).releaseCoupon(any(), any());
        verify(productInternalApi, never()).restoreStock(any());
    }

    @Test
    void cancelPaidAfterRefundReleasesCoupons() {
        Order order = order(OrderStatus.PAID.name(), 10L);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(java.util.Optional.of(order));
        RefundResultVO success = new RefundResultVO();
        success.setSuccess(true);
        when(paymentInternalApi.refund(any(RefundRequest.class))).thenReturn(Result.success(success));
        when(orderRepository.markRefundedAfterCancel(eq(ORDER_NO), any(LocalDateTime.class))).thenReturn(true);
        when(orderRepository.findItems(ORDER_NO)).thenReturn(List.of());
        when(orderRepository.findCoupons(ORDER_NO)).thenReturn(List.of());
        lenient().when(productInternalApi.restoreStock(any(StockChangeRequest.class)))
                .thenReturn(Result.success(null));
        lenient().when(discountInternalApi.releaseCoupon(any(Long.class), any(CouponReleaseRequest.class)))
                .thenReturn(Result.success(null));

        service().cancel(USER, ORDER_NO);
        verify(discountInternalApi).releaseCoupon(eq(10L), any(CouponReleaseRequest.class));
    }
}
