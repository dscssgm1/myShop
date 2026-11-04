package com.four_person.discount;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 优惠券黑盒：模板发放（平台/商家范围规则、归属校验）、领取边界（限领/库存/时间窗）、
 * 下架权限、我的券状态与过期标记。
 */
class CouponApiBBTTest extends DiscountApiBBTSupport {

    private static final String URI = "/coupons";

    private Map<String, Object> templateRequest() {
        return body("title", "BBT-满减券", "thresholdAmount", 50, "discountAmount", 10,
                "scopeType", "ALL", "totalStock", 100, "perUserLimit", 2,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7));
    }

    private long createTemplate(RequestPostProcessor owner) throws Exception {
        return longOf(okData(post(URI + "/templates", templateRequest(), owner)), "id");
    }

    private long claim(long userId, long templateId) throws Exception {
        return longOf(okData(post(URI + "/" + templateId + "/claim", null, customer(userId))), "id");
    }

    /** 结算页商品明细：A1 33.33（分类 7）+ A2 66.66（分类 8），均在店铺 A */
    private List<Map<String, Object>> orderItems() {
        return List.of(
                item(PRODUCT_A1, SHOP_A, 7L, "33.33", 1),
                item(PRODUCT_A2, SHOP_A, 8L, "66.66", 1));
    }

    private JsonNode claimable(RequestPostProcessor user) throws Exception {
        return okData(post(URI + "/claimable", body("items", orderItems()), user));
    }

    private List<Long> mineIds(long userId, String status) throws Exception {
        String uri = status == null ? URI + "/mine" : URI + "/mine?status=" + status;
        JsonNode list = okData(get(uri, customer(userId)));
        List<Long> ids = new java.util.ArrayList<>();
        list.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private JsonNode nodeWithId(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return org.assertj.core.api.Assertions.fail("列表中找不到 id=%s 的节点: %s", id, list);
    }

    // ==================== 模板发放 ====================

    @Test
    void platformCreateCouponTemplate() throws Exception {
        JsonNode vo = okData(post(URI + "/templates", templateRequest(), platform()));

        long id = longOf(vo, "id");
        assertThat(vo.get("shopId").asLong()).isZero();
        assertThat(vo.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(vo.get("remaining").asInt()).isEqualTo(100);
        assertThat(vo.get("scopeType").asText()).isEqualTo("ALL");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT shop_id, status, claimed_count FROM coupon_template WHERE id = ?", id);
        assertThat(((Number) row.get("shop_id")).longValue()).isZero();
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) row.get("claimed_count")).intValue()).isZero();
    }

    @Test
    void merchantCouponScopeCorrectedToShopOrCategory() throws Exception {
        // 商家传 ALL → 服务端纠正为本店
        JsonNode vo = okData(post(URI + "/templates", templateRequest(), merchantA()));
        assertThat(longOf(vo, "shopId")).isEqualTo(SHOP_A);
        assertThat(vo.get("scopeType").asText()).isEqualTo("SHOP");
        assertThat(vo.get("shopName").asText()).isEqualTo("黑盒店铺A");

        // 显式 CATEGORY → 保留分类范围（商家侧按本店商品匹配）
        Map<String, Object> categoryRequest = templateRequest();
        categoryRequest.put("title", "BBT-分类券");
        categoryRequest.put("scopeType", "CATEGORY");
        categoryRequest.put("scopeIds", List.of(7L));
        JsonNode categoryVo = okData(post(URI + "/templates", categoryRequest, merchantA()));
        assertThat(categoryVo.get("scopeType").asText()).isEqualTo("CATEGORY");
        assertThat(categoryVo.get("scopeIds").get(0).asLong()).isEqualTo(7L);

        // 指定商品范围已下线 → 拒绝
        Map<String, Object> productRequest = templateRequest();
        productRequest.put("title", "BBT-商品券");
        productRequest.put("scopeType", "PRODUCT");
        productRequest.put("scopeIds", List.of(PRODUCT_A1));
        JsonNode root = expectCode(post(URI + "/templates", productRequest, merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("已不支持指定商品范围");
    }

    @Test
    void platformShopScopeRejected() throws Exception {
        Map<String, Object> request = templateRequest();
        request.put("scopeType", "SHOP");
        JsonNode root = expectCode(post(URI + "/templates", request, platform()), 40007);
        assertThat(root.get("message").asText()).contains("平台券不能使用店铺范围");
    }

    @Test
    void merchantProductScopeRejected() throws Exception {
        Map<String, Object> productRequest = templateRequest();
        productRequest.put("title", "BBT-商品券");
        productRequest.put("scopeType", "PRODUCT");
        productRequest.put("scopeIds", List.of(PRODUCT_B1));
        JsonNode root = expectCode(post(URI + "/templates", productRequest, merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("已不支持指定商品范围");
    }

    @Test
    void createTemplateFieldValidation() throws Exception {
        String uri = URI + "/templates";
        expectCode(post(uri, body("title", " ", "thresholdAmount", 0, "discountAmount", 10,
                "totalStock", 1, "perUserLimit", 1), platform()), 400);
        expectCode(post(uri, body("title", "BBT-零面额", "thresholdAmount", 0, "discountAmount", 0,
                "totalStock", 1, "perUserLimit", 1), platform()), 400);
        expectCode(post(uri, body("title", "BBT-零库存", "thresholdAmount", 0, "discountAmount", 5,
                "totalStock", 0, "perUserLimit", 1), platform()), 400);
        expectCode(post(uri, body("title", "BBT-零限领", "thresholdAmount", 0, "discountAmount", 5,
                "totalStock", 1, "perUserLimit", 0), platform()), 400);
        expectCode(post(uri, body("title", "BBT-负门槛", "thresholdAmount", -1, "discountAmount", 5,
                "totalStock", 1, "perUserLimit", 1), platform()), 400);
    }

    @Test
    void createTemplateInvertedWindowRejected() throws Exception {
        Map<String, Object> request = templateRequest();
        request.put("validStart", timeFromNow(3));
        request.put("validEnd", timeFromNow(1));
        JsonNode root = expectCode(post(URI + "/templates", request, platform()), 40007);
        assertThat(root.get("message").asText()).contains("有效期开始时间不能晚于结束时间");
    }

    @Test
    void createTemplateRequiresPlatformOrMerchantGroup() throws Exception {
        expectCode(post(URI + "/templates", templateRequest()), 403);
        expectCode(post(URI + "/templates", templateRequest(), customer(USER_1)), 403);
    }

    // ==================== 结算页本单可领 ====================

    @Test
    void claimableFiltersByOrderScopeAndThreshold() throws Exception {
        long usable = longOf(okData(post(URI + "/templates", body(
                "title", "BBT-本单可领满80减30", "thresholdAmount", 80, "discountAmount", 30,
                "scopeType", "ALL", "totalStock", 100, "perUserLimit", 5,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), platform())), "id");
        // 门槛未达 → 不列出
        long aboveThreshold = longOf(okData(post(URI + "/templates", body(
                "title", "BBT-本单可领满200减50", "thresholdAmount", 200, "discountAmount", 50,
                "scopeType", "ALL", "totalStock", 100, "perUserLimit", 5,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), platform())), "id");
        // 商家券：本单含该店商品 → 列出；本单无该店商品 → 不列出
        long shopACoupon = longOf(okData(post(URI + "/templates", body(
                "title", "BBT-A店本单券", "thresholdAmount", 50, "discountAmount", 10,
                "scopeType", "SHOP", "totalStock", 100, "perUserLimit", 5,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), merchantA())), "id");
        long shopBCoupon = longOf(okData(post(URI + "/templates", body(
                "title", "BBT-B店本单券", "thresholdAmount", 50, "discountAmount", 10,
                "scopeType", "SHOP", "totalStock", 100, "perUserLimit", 5,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), merchantB())), "id");

        JsonNode list = claimable(customer(USER_1));
        assertThat(list).hasSize(2);
        Map<Long, JsonNode> byId = new java.util.HashMap<>();
        list.forEach(node -> byId.put(node.get("id").asLong(), node));
        assertThat(byId).containsKeys(usable, shopACoupon);
        assertThat(byId).doesNotContainKeys(aboveThreshold, shopBCoupon);
        // 每张带本单可抵扣金额（范围内小计达门槛后的实际抵扣额）
        assertThat(byId.get(usable).get("applicableAmount").doubleValue()).isEqualTo(30);
        assertThat(byId.get(shopACoupon).get("applicableAmount").doubleValue()).isEqualTo(10);
        assertThat(byId.get(usable).get("claimed").asBoolean()).isFalse();
    }

    @Test
    void claimableExcludesPerUserLimitReached() throws Exception {
        Map<String, Object> limited = templateRequest();
        limited.put("title", "BBT-限领1张");
        limited.put("perUserLimit", 1);
        long limitedId = longOf(okData(post(URI + "/templates", limited, platform())), "id");

        // 未领：列出且 claimed=false
        assertThat(nodeWithId(claimable(customer(USER_1)), limitedId).get("claimed").asBoolean()).isFalse();

        // 已达个人限领：不再列出；其他用户不受影响
        claim(USER_1, limitedId);
        assertThat(claimable(customer(USER_1))).isEmpty();
        assertThat(claimable(customer(USER_2))).hasSize(1);
    }

    @Test
    void claimableExcludesStockEmptyTemplates() throws Exception {
        Map<String, Object> single = templateRequest();
        single.put("title", "BBT-单库存");
        single.put("totalStock", 1);
        single.put("perUserLimit", 5);
        long id = longOf(okData(post(URI + "/templates", single, platform())), "id");
        claim(USER_1, id);

        // 库存已领完 → 其他用户也不再列出
        assertThat(claimable(customer(USER_2))).isEmpty();
    }

    // ==================== 领取边界 ====================

    @Test
    void claimPerUserLimitBoundary() throws Exception {
        long templateId = createTemplate(platform());
        claim(USER_1, templateId);
        claim(USER_1, templateId); // 限领 2：第二张仍可领
        expectCode(post(URI + "/" + templateId + "/claim", null, customer(USER_1)), 40006);

        // 换个用户不受他人限领影响
        claim(USER_2, templateId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT claimed_count FROM coupon_template WHERE id = ?", Integer.class, templateId))
                .isEqualTo(3);
    }

    @Test
    void claimStockExhaustedAcrossUsers() throws Exception {
        Map<String, Object> request = templateRequest();
        request.put("title", "BBT-仅一张");
        request.put("totalStock", 1);
        request.put("perUserLimit", 5);
        long templateId = longOf(okData(post(URI + "/templates", request, platform())), "id");

        claim(USER_1, templateId);
        expectCode(post(URI + "/" + templateId + "/claim", null, customer(USER_2)), 40005);
    }

    @Test
    void claimOutsideValidityWindowRejected() throws Exception {
        Map<String, Object> notStarted = templateRequest();
        notStarted.put("title", "BBT-未开始");
        notStarted.put("validStart", timeFromNow(1));
        long future = longOf(okData(post(URI + "/templates", notStarted, platform())), "id");
        expectCode(post(URI + "/" + future + "/claim", null, customer(USER_1)), 40003);

        Map<String, Object> endedWindow = templateRequest();
        endedWindow.put("title", "BBT-已过期窗口");
        endedWindow.put("validEnd", timeFromNow(-1));
        long past = longOf(okData(post(URI + "/templates", endedWindow, platform())), "id");
        expectCode(post(URI + "/" + past + "/claim", null, customer(USER_1)), 40003);
    }

    @Test
    void claimRequiresLoginAndExistingTemplate() throws Exception {
        long templateId = createTemplate(platform());
        // 无身份头 → 401
        mockMvc.perform(MockMvcRequestBuilders.post(URI + "/" + templateId + "/claim"))
                .andExpect(result -> assertThat(
                        objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt())
                        .isEqualTo(401));
        // 模板不存在 → 40404
        expectCode(post(URI + "/99999/claim", null, customer(USER_1)), 40404);
    }

    // ==================== 下架与列表 ====================

    @Test
    void endTemplatePermissionMatrix() throws Exception {
        long platformTemplate = createTemplate(platform());
        long shopATemplate = createTemplate(merchantA());

        // 商家券不能走平台端下架
        expectCode(post(URI + "/templates/" + shopATemplate + "/end", null, platform()), 403);
        // B 店不能下架 A 店的券，也不能下架平台券
        expectCode(post(URI + "/templates/" + shopATemplate + "/end", null, merchantB()), 403);
        expectCode(post(URI + "/templates/" + platformTemplate + "/end", null, merchantA()), 403);

        // 各自能下架自己的
        expectCode(post(URI + "/templates/" + shopATemplate + "/end", null, merchantA()), 200);
        expectCode(post(URI + "/templates/" + platformTemplate + "/end", null, platform()), 200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM coupon_template WHERE id = ?", String.class, shopATemplate))
                .isEqualTo("ENDED");

        // 下架后不可领
        expectCode(post(URI + "/" + shopATemplate + "/claim", null, customer(USER_1)), 40003);
    }

    @Test
    void myTemplatesOnlyReturnsOwnShop() throws Exception {
        long shopA = createTemplate(merchantA());
        createTemplate(merchantB());

        JsonNode mineA = okData(get(URI + "/templates/mine", merchantA()));
        assertThat(mineA).hasSize(1);
        assertThat(mineA.get(0).get("id").asLong()).isEqualTo(shopA);
    }

    @Test
    void templatesMarkClaimedForCurrentUser() throws Exception {
        long templateId = createTemplate(platform());
        claim(USER_1, templateId);

        // 列表混有存量真实券，按 id 定位本用例的券
        JsonNode mine = okData(get(URI + "/templates", customer(USER_1)));
        assertThat(nodeWithId(mine, templateId).get("claimed").asBoolean()).isTrue();

        JsonNode others = okData(get(URI + "/templates", customer(USER_2)));
        assertThat(nodeWithId(others, templateId).get("claimed").asBoolean()).isFalse();

        // 游客（无身份头）不报错
        mockMvc.perform(MockMvcRequestBuilders.get(URI + "/templates"))
                .andExpect(result -> assertThat(
                        objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt())
                        .isEqualTo(200));
    }

    @Test
    void mineFiltersByStatusAndMarksExpired() throws Exception {
        long templateId = createTemplate(platform());
        long couponId = claim(USER_1, templateId);

        // 有效期内：UNUSED 可查
        assertThat(mineIds(USER_1, "UNUSED")).contains(couponId);
        assertThat(mineIds(USER_1, "USED")).doesNotContain(couponId);

        // 时间流逝到过期后：全量 /mine 触发惰性过期标记
        jdbcTemplate.update("UPDATE user_coupon SET expired_at = ? WHERE id = ?",
                java.time.LocalDateTime.now().minusMinutes(1), couponId);
        JsonNode all = okData(get(URI + "/mine", customer(USER_1)));
        assertThat(nodeWithId(all, couponId).get("status").asText()).isEqualTo("EXPIRED");

        // 标记落库后：EXPIRED 可查、UNUSED 不再出现
        assertThat(mineIds(USER_1, "EXPIRED")).contains(couponId);
        assertThat(mineIds(USER_1, "UNUSED")).doesNotContain(couponId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM user_coupon WHERE id = ?", String.class, couponId)).isEqualTo("EXPIRED");

        // 非法状态值 → 退回全量列表
        assertThat(mineIds(USER_1, "WHATEVER")).contains(couponId);
    }

    @Test
    void mineRequiresLogin() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(URI + "/mine"))
                .andExpect(result -> assertThat(
                        objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt())
                        .isEqualTo(401));
    }
}
