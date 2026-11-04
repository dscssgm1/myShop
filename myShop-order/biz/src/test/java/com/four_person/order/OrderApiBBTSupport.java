package com.four_person.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.Result;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.logistics.api.LogisticsInternalApi;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.merchant.api.MerchantInternalApi;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.payment.api.PaymentInternalApi;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

/**
 * 订单模块黑盒测试基类：真实 MySQL（本机 docker 13307）+ MockMvc 走完整 HTTP 栈。
 * 隔离约定：
 * - 测试数据统一用 900xxx 高位 ID / MS-BBT 支付单号，前后各清理一次；
 * - 7 个下游 Feign 客户端全部 MockBean（商品/折扣/支付/物流/商家/会员/消息），
 *   商品目录固定 900301/900302/900303 三个测试商品，优惠券核销与释放放行并留痕可 verify；
 * - 折扣算价默认「无优惠原价」，需要券/活动的用例再覆盖 calculate 桩。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class OrderApiBBTSupport {

    protected static final long SHOP_A = 900101L;
    protected static final long SHOP_B = 900102L;
    protected static final long USER_1 = 900201L;
    protected static final long USER_2 = 900202L;
    protected static final long PRODUCT_A1 = 900301L; // A 店 · 分类 7 · ¥33.33 · 库存 100
    protected static final long PRODUCT_A2 = 900302L; // A 店 · 分类 8 · ¥66.66 · 库存 100
    protected static final long PRODUCT_B1 = 900303L; // B 店 · 分类 7 · ¥20.00 · 库存 100
    protected static final long COUPON_1 = 800001L;
    protected static final long COUPON_2 = 800002L;

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected ObjectMapper objectMapper;

    @MockBean protected ProductInternalApi productInternalApi;
    @MockBean protected DiscountInternalApi discountInternalApi;
    @MockBean protected PaymentInternalApi paymentInternalApi;
    @MockBean protected LogisticsInternalApi logisticsInternalApi;
    @MockBean protected MerchantInternalApi merchantInternalApi;
    @MockBean protected com.four_person.system.api.MemberInternalApi memberInternalApi;
    @MockBean protected MessageInternalApi messageInternalApi;

    @BeforeEach
    void setUpBlackBox() {
        cleanupTestData();
        stubPorts();
    }

    @AfterEach
    void tearDownBlackBox() {
        cleanupTestData();
    }

    // ==================== 数据隔离 ====================

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE oi FROM order_item oi JOIN order_info o ON oi.order_no = o.order_no "
                + "WHERE o.user_id >= 900201");
        jdbcTemplate.update("DELETE oc FROM order_coupon oc JOIN order_info o ON oc.order_no = o.order_no "
                + "WHERE o.user_id >= 900201");
        jdbcTemplate.update("DELETE FROM order_group WHERE user_id >= 900201");
        jdbcTemplate.update("DELETE FROM order_info WHERE user_id >= 900201");
    }

    // ==================== 下游端口桩 ====================

    private void stubPorts() {
        lenient().when(productInternalApi.batch(any())).thenAnswer(invocation -> {
            ProductBatchRequest request = invocation.getArgument(0);
            List<ProductVO> products = new ArrayList<>();
            for (Long id : request.getIds()) {
                ProductVO product = catalog().get(id);
                if (product != null) {
                    products.add(product);
                }
            }
            return Result.success(products);
        });
        lenient().when(productInternalApi.deductStock(any())).thenReturn(Result.success(null));
        lenient().when(productInternalApi.restoreStock(any())).thenReturn(Result.success(null));
        lenient().when(productInternalApi.commissionRates(any())).thenReturn(Result.success(List.of()));

        // 默认算价：无活动无券按原价；具体用例需要优惠时再覆盖
        lenient().when(discountInternalApi.calculate(any())).thenAnswer(invocation -> {
            DiscountCalculateRequest request = invocation.getArgument(0);
            BigDecimal original = request.getItems().stream()
                    .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            return Result.success(discountResult(original, "0.00", "0.00", original, "0.00", "0.00"));
        });
        lenient().when(discountInternalApi.useCoupon(any(), any())).thenReturn(Result.success(null));
        lenient().when(discountInternalApi.releaseCoupon(any(), any())).thenReturn(Result.success(null));

        lenient().when(paymentInternalApi.refund(any())).thenAnswer(invocation -> {
            RefundRequest request = invocation.getArgument(0);
            RefundResultVO vo = new RefundResultVO();
            vo.setSuccess(true);
            vo.setRefunded(true);
            vo.setPaymentNo("BBT-PAY-REFUND");
            vo.setAmount(request.getAmount());
            vo.setStatus("SUCCESS");
            return Result.success(vo);
        });
        lenient().when(paymentInternalApi.paymentStatus(any())).thenReturn(Result.success("SUCCESS"));

        lenient().when(logisticsInternalApi.createShipment(any())).thenAnswer(invocation -> {
            LogisticsDtos.ShipmentVO vo = new LogisticsDtos.ShipmentVO();
            vo.setTrackingNo("BBT-TRK-" + System.nanoTime());
            vo.setOrderNo(((LogisticsDtos.ShipmentCreateRequest) invocation.getArgument(0)).getOrderNo());
            vo.setStatus("PICKUP");
            return Result.success(vo);
        });
        lenient().when(merchantInternalApi.shop(any())).thenAnswer(invocation -> {
            MerchantDtos.ShopVO vo = new MerchantDtos.ShopVO();
            vo.setId(invocation.getArgument(0));
            vo.setName(shopNameOf(invocation.getArgument(0)));
            vo.setAddress("黑盒市测试区揽收路 1 号");
            vo.setPhone("13800000000");
            return Result.success(vo);
        });
        lenient().when(memberInternalApi.earn(any())).thenReturn(Result.success(10));
        lenient().when(memberInternalApi.refundEarn(any())).thenReturn(Result.success(5));
        lenient().when(messageInternalApi.pushNotice(any())).thenReturn(Result.success(null));
    }

    private Map<Long, ProductVO> catalog() {
        Map<Long, ProductVO> catalog = new LinkedHashMap<>();
        catalog.put(PRODUCT_A1, product(PRODUCT_A1, SHOP_A, 7L, "33.33"));
        catalog.put(PRODUCT_A2, product(PRODUCT_A2, SHOP_A, 8L, "66.66"));
        catalog.put(PRODUCT_B1, product(PRODUCT_B1, SHOP_B, 7L, "20.00"));
        return catalog;
    }

    private ProductVO product(long id, long shopId, long categoryId, String price) {
        ProductVO vo = new ProductVO();
        vo.setId(id);
        vo.setName("黑盒商品" + id);
        vo.setShopId(shopId);
        vo.setShopName(shopNameOf(shopId));
        vo.setCategoryId(categoryId);
        vo.setCategoryName("分类" + categoryId);
        vo.setPrice(new BigDecimal(price));
        vo.setStock(100);
        vo.setStatus(1);
        vo.setEmoji("🛍️");
        vo.setColor("#409EFF");
        return vo;
    }

    private String shopNameOf(Long shopId) {
        if (SHOP_A == shopId) {
            return "黑盒店铺A";
        }
        if (SHOP_B == shopId) {
            return "黑盒店铺B";
        }
        return "店铺#" + shopId;
    }

    /** 组装折扣算价结果（金额均两位小数字符串） */
    protected DiscountResultVO discountResult(BigDecimal original, String promotion, String coupon,
                                              BigDecimal pay, String merchantBear, String platformBear) {
        DiscountResultVO vo = new DiscountResultVO();
        vo.setOriginalAmount(original);
        vo.setPromotionDiscount(new BigDecimal(promotion));
        vo.setCouponDiscount(new BigDecimal(coupon));
        vo.setTotalDiscount(original.subtract(pay));
        vo.setPayAmount(pay);
        vo.setMerchantBearAmount(new BigDecimal(merchantBear));
        vo.setPlatformBearAmount(new BigDecimal(platformBear));
        return vo;
    }

    protected DiscountResultVO.CouponUsage usage(long couponId, String title, String discount) {
        DiscountResultVO.CouponUsage usage = new DiscountResultVO.CouponUsage();
        usage.setCouponId(couponId);
        usage.setTitle(title);
        usage.setDiscount(new BigDecimal(discount));
        return usage;
    }

    // ==================== 身份与请求 ====================

    protected RequestPostProcessor customer(long userId) {
        return headers(userId, "USER", null);
    }

    protected RequestPostProcessor merchantA() {
        return headers(2001L, "MERCHANT", SHOP_A);
    }

    private RequestPostProcessor headers(long userId, String group, Long shopId) {
        return (MockHttpServletRequest request) -> {
            request.addHeader(UserContext.USER_ID_HEADER, String.valueOf(userId));
            request.addHeader(UserContext.GROUP_HEADER, group);
            if (shopId != null) {
                request.addHeader(UserContext.SHOP_ID_HEADER, String.valueOf(shopId));
            }
            return request;
        };
    }

    protected Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                map.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return map;
    }

    protected Map<String, Object> orderItem(long productId, int quantity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId);
        item.put("quantity", quantity);
        return item;
    }

    protected ResultActions post(String uri, Object requestBody, RequestPostProcessor... users) throws Exception {
        return perform(MockMvcRequestBuilders.post(uri), requestBody, users);
    }

    protected ResultActions get(String uri, RequestPostProcessor... users) throws Exception {
        return perform(MockMvcRequestBuilders.get(uri), null, users);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, Object requestBody,
                                  RequestPostProcessor... users) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        if (requestBody != null) {
            builder.content(objectMapper.writeValueAsString(requestBody));
        }
        for (RequestPostProcessor user : users) {
            builder.with(user);
        }
        return mockMvc.perform(builder);
    }

    // ==================== 断言辅助 ====================

    protected JsonNode okData(ResultActions actions) throws Exception {
        JsonNode root = readTree(actions);
        assertThat(root.get("code").asInt()).as("响应码: %s", root).isEqualTo(200);
        return root.get("data");
    }

    protected JsonNode expectCode(ResultActions actions, int code) throws Exception {
        JsonNode root = readTree(actions);
        assertThat(root.get("code").asInt()).as("响应体: %s", root).isEqualTo(code);
        return root;
    }

    protected JsonNode readTree(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        String json = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    protected java.math.BigDecimal dec(JsonNode node, String field) {
        assertThat(node.hasNonNull(field)).as("缺少字段 %s: %s", field, node).isTrue();
        return new BigDecimal(node.get(field).asText());
    }

    protected java.math.BigDecimal decOf(Map<String, Object> row, String column) {
        Object value = row.get(column);
        assertThat(value).as("缺少列 %s", column).isNotNull();
        return new BigDecimal(String.valueOf(value));
    }

    // ==================== 业务辅助 ====================

    /** 下单：items + 可选券，返回 CreateOrderVO data 节点 */
    protected JsonNode createOrder(long userId, List<Map<String, Object>> items, List<Long> couponIds)
            throws Exception {
        return createOrder(userId, items, couponIds, null);
    }

    /** 下单：items + 可选券 + 可选活动选择（null = 引擎自动最优） */
    protected JsonNode createOrder(long userId, List<Map<String, Object>> items, List<Long> couponIds,
                                   List<Long> promotionIds) throws Exception {
        Map<String, Object> requestBody = body(
                "items", items,
                "couponIds", couponIds == null || couponIds.isEmpty() ? null : couponIds,
                // null = 省略该字段（引擎自动最优）；空列表 = 明确不使用活动
                "promotionIds", promotionIds,
                "receiverName", "黑盒收件人",
                "receiverPhone", "13900000001",
                "receiverAddress", "黑盒市测试区收件路 2 号");
        return okData(post("/orders", requestBody, customer(userId)));
    }

    /** 跨店合单固定场景：A 店 ¥33.33 + B 店 ¥20.00，跨店满减 3.33 + 券 10.00，实付 40.00 */
    protected void stubCrossShopDiscount() {
        DiscountResultVO discount = discountResult(new BigDecimal("53.33"), "3.33", "10.00",
                new BigDecimal("40.00"), "4.00", "9.33");
        discount.setAppliedPromotionId(700001L);
        discount.setAppliedPromotionTitle("BBT跨店满减");
        discount.setAppliedCouponId(COUPON_1);
        discount.setAppliedCouponTitle("BBT满减券");
        discount.setAppliedCoupons(List.of(usage(COUPON_1, "BBT满减券", "10.00")));
        // doReturn 形式：不触发 setUp 里的默认 thenAnswer 桩（when 形式会以 null 参数执行旧 answer）
        doReturn(Result.success(discount)).when(discountInternalApi).calculate(any());
    }

    /** 创建跨店合单（A1 + B1 + 券），返回 CreateOrderVO data 节点 */
    protected JsonNode createCrossShopGroup(long userId) throws Exception {
        stubCrossShopDiscount();
        return createOrder(userId, List.of(orderItem(PRODUCT_A1, 1), orderItem(PRODUCT_B1, 1)),
                List.of(COUPON_1));
    }

    /** 组支付回调（黑盒视角：模拟支付服务回调） */
    protected void payGroup(String groupNo) throws Exception {
        expectCode(post("/internal/order-groups/" + groupNo + "/paid",
                body("paymentNo", "BBT-PAY-" + groupNo)), 200);
    }

    /** 单订单支付回调（构造「部分已支付」混合态用） */
    protected void paySingle(String orderNo) throws Exception {
        expectCode(post("/internal/orders/" + orderNo + "/paid",
                body("paymentNo", "BBT-PAY-" + orderNo)), 200);
    }

    // ==================== DB 读取 ====================

    protected Map<String, Object> groupRow(String groupNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM order_group WHERE group_no = ?", groupNo);
        assertThat(rows).as("订单组 %s 不存在", groupNo).hasSize(1);
        return rows.get(0);
    }

    protected Map<String, Object> orderRow(String orderNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM order_info WHERE order_no = ?", orderNo);
        assertThat(rows).as("订单 %s 不存在", orderNo).hasSize(1);
        return rows.get(0);
    }

    protected String statusOf(String orderNo) {
        return (String) orderRow(orderNo).get("status");
    }

    /** 组内子订单号（按创建顺序） */
    protected List<String> childOrderNos(String groupNo) {
        return jdbcTemplate.queryForList(
                "SELECT order_no FROM order_info WHERE group_no = ? ORDER BY id", String.class, groupNo);
    }
}
