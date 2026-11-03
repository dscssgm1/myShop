package com.four_person.discount;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.web.UserContext;
import com.four_person.discount.application.port.ProductQueryPort;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.ProductSnapshot;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 折扣模块黑盒测试基类：真实 MySQL（本机 docker 13307）+ MockMvc 走完整 HTTP 栈。
 * 隔离约定：
 * - 测试数据统一用 BBT- 前缀标题 / 900xxx 的高位 ID，前后各清理一次；
 * - 库中已有活动会污染算价断言，测试期间快照 status 并整体暂停，结束后恢复；
 * - discount 配置组（config_item）同样快照/恢复，供开关类用例自由改写；
 * - 商品/店铺查询端口 MockBean，固定返回 900301/900302/900303 三个测试商品。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class DiscountApiBBTSupport {

    protected static final long SHOP_A = 900101L;
    protected static final long SHOP_B = 900102L;
    protected static final long USER_1 = 900201L;
    protected static final long USER_2 = 900202L;
    protected static final long PRODUCT_A1 = 900301L; // A 店 · 分类 7 · ¥33.33
    protected static final long PRODUCT_A2 = 900302L; // A 店 · 分类 8 · ¥66.66
    protected static final long PRODUCT_B1 = 900303L; // B 店 · 分类 7 · ¥20.00

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected ObjectMapper objectMapper;
    @MockBean protected ProductQueryPort productQueryPort;
    @MockBean protected ShopQueryPort shopQueryPort;

    private Map<String, String> configSnapshot;
    private List<Map<String, Object>> promotionStatusSnapshot;

    @BeforeEach
    void setUpBlackBox() {
        cleanupTestData();
        snapshotDbState();
        stubPorts();
    }

    @AfterEach
    void tearDownBlackBox() {
        restoreDbState();
        cleanupTestData();
    }

    // ==================== 数据隔离 ====================

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM promotion WHERE title LIKE 'BBT-%'");
        jdbcTemplate.update("DELETE FROM coupon_template WHERE title LIKE 'BBT-%'");
        jdbcTemplate.update("DELETE FROM user_coupon WHERE user_id >= 900201");
        jdbcTemplate.update("DELETE FROM promotion_enrollment WHERE shop_id >= 900101");
    }

    private void snapshotDbState() {
        configSnapshot = new HashMap<>();
        jdbcTemplate.query("SELECT config_key, config_value FROM config_item WHERE config_group = 'discount'",
                rs -> {
                    configSnapshot.put(rs.getString("config_key"), rs.getString("config_value"));
                });
        promotionStatusSnapshot = jdbcTemplate.queryForList(
                "SELECT id, status FROM promotion WHERE title NOT LIKE 'BBT-%'");
        // 测试期间暂停所有存量活动，让算价断言只受 BBT- 活动影响
        jdbcTemplate.update("UPDATE promotion SET status = 'ENDED' WHERE title NOT LIKE 'BBT-%'");
    }

    private void restoreDbState() {
        if (promotionStatusSnapshot != null) {
            for (Map<String, Object> row : promotionStatusSnapshot) {
                jdbcTemplate.update("UPDATE promotion SET status = ? WHERE id = ?",
                        row.get("status"), row.get("id"));
            }
        }
        if (configSnapshot != null) {
            configSnapshot.forEach((key, value) ->
                    jdbcTemplate.update("UPDATE config_item SET config_value = ? " +
                            "WHERE config_group = 'discount' AND config_key = ?", value, key));
        }
    }

    /** 修改 discount 配置开关（结束后由基类恢复） */
    protected void setConfig(String key, String value) {
        jdbcTemplate.update("UPDATE config_item SET config_value = ? " +
                "WHERE config_group = 'discount' AND config_key = ?", value, key);
    }

    private void stubPorts() {
        lenient().when(productQueryPort.batch(any())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(this::snapshotOf).filter(Objects::nonNull).toList();
        });
        lenient().when(shopQueryPort.shopName(any())).thenAnswer(invocation ->
                shopNameOf(invocation.getArgument(0)));
    }

    private ProductSnapshot snapshotOf(Long id) {
        return switch (id.intValue()) {
            case (int) PRODUCT_A1 -> snapshot(id, SHOP_A, 7L, "33.33");
            case (int) PRODUCT_A2 -> snapshot(id, SHOP_A, 8L, "66.66");
            case (int) PRODUCT_B1 -> snapshot(id, SHOP_B, 7L, "20.00");
            default -> null; // 未登记的商品视为不存在
        };
    }

    private ProductSnapshot snapshot(Long id, long shopId, long categoryId, String price) {
        return new ProductSnapshot(id, "商品" + id, Money.of(price), categoryId, "分类" + categoryId,
                shopId, shopNameOf(shopId), "🛍️", "#409EFF", List.of(), 100, new BigDecimal("4.9"));
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

    // ==================== 身份与请求 ====================

    protected RequestPostProcessor platform() {
        return headers(1L, "PLATFORM", null);
    }

    protected RequestPostProcessor merchantA() {
        return headers(2001L, "MERCHANT", SHOP_A);
    }

    protected RequestPostProcessor merchantB() {
        return headers(2002L, "MERCHANT", SHOP_B);
    }

    protected RequestPostProcessor customer(long userId) {
        return headers(userId, "USER", null);
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

    protected ResultActions post(String uri, Object body, RequestPostProcessor... users) throws Exception {
        return perform(MockMvcRequestBuilders.post(uri), body, users);
    }

    protected ResultActions put(String uri, Object body, RequestPostProcessor... users) throws Exception {
        return perform(MockMvcRequestBuilders.put(uri), body, users);
    }

    protected ResultActions get(String uri, RequestPostProcessor... users) throws Exception {
        return perform(MockMvcRequestBuilders.get(uri), null, users);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body,
                                  RequestPostProcessor... users) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        for (RequestPostProcessor user : users) {
            builder.with(user);
        }
        return mockMvc.perform(builder);
    }

    // ==================== 断言辅助 ====================

    /** 断言 HTTP 200 + 业务码 200，返回 data 节点 */
    protected JsonNode okData(ResultActions actions) throws Exception {
        JsonNode root = readTree(actions);
        assertThat(root.get("code").asInt()).as("响应码: %s", root).isEqualTo(200);
        return root.get("data");
    }

    /** 断言业务错误码（异常统一 HTTP 200 + Result.code） */
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

    protected JsonNode createActivity(ResultActions actions) throws Exception {
        return okData(actions);
    }

    protected long longOf(JsonNode node, String field) {
        assertThat(node.hasNonNull(field)).as("缺少字段 %s: %s", field, node).isTrue();
        return node.get(field).asLong();
    }

    protected static String timeFromNow(int days) {
        return LocalDateTime.now().plusDays(days).withNano(0).format(TIME);
    }

    protected static Map<String, Object> item(long productId, long shopId, long categoryId,
                                              String price, int quantity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId);
        item.put("shopId", shopId);
        item.put("categoryId", categoryId);
        item.put("price", new BigDecimal(price));
        item.put("quantity", quantity);
        return item;
    }
}
