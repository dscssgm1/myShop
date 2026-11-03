package com.four_person.discount;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动管理黑盒：平台/商家发布、范围归属校验、时间窗校验、权限边界、结束与状态过滤。
 */
class PromotionApiBBTTest extends DiscountApiBBTSupport {

    private static final String URI = "/promotions";

    private Map<String, Object> fullReductionRequest() {
        return body("title", "BBT-平台满减", "type", "平台大促",
                "ruleType", "FULL_REDUCTION", "thresholdAmount", 100,
                "discountAmount", 20, "scopeType", "ALL",
                "couponStackable", true, "enrollRequired", false,
                "platformBearRate", 50,
                "startTime", timeFromNow(-1), "endTime", timeFromNow(7));
    }

    // ==================== 平台发布 ====================

    @Test
    void platformCreateFullReductionActivity() throws Exception {
        JsonNode vo = okData(post(URI, fullReductionRequest(), platform()));

        long id = longOf(vo, "id");
        assertThat(vo.get("shopId").asLong()).isZero();
        assertThat(vo.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(vo.get("scopeType").asText()).isEqualTo("ALL");
        assertThat(vo.get("couponStackable").asBoolean()).isTrue();
        assertThat(vo.get("platformBearRate").doubleValue()).isEqualTo(50);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT shop_id, status FROM promotion WHERE id = ?", id);
        assertThat(((Number) row.get("shop_id")).longValue()).isZero();
        assertThat(row.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void platformCreateDiscountRuleActivity() throws Exception {
        JsonNode vo = okData(post(URI, body("title", "BBT-平台折扣", "ruleType", "DISCOUNT",
                "thresholdAmount", 200, "discountRate", 0.8, "maxDiscount", 30,
                "scopeType", "CATEGORY", "scopeIds", List.of(7L)), platform()));

        assertThat(vo.get("ruleType").asText()).isEqualTo("DISCOUNT");
        assertThat(vo.get("discountRate").doubleValue()).isEqualTo(0.8);
        assertThat(vo.get("maxDiscount").doubleValue()).isEqualTo(30);
        assertThat(vo.get("scopeType").asText()).isEqualTo("CATEGORY");
        assertThat(vo.get("scopeIds")).hasSize(1);
        assertThat(vo.get("scopeIds").get(0).asLong()).isEqualTo(7L);
    }

    @Test
    void createBlankTitleRejectedByValidation() throws Exception {
        Map<String, Object> request = fullReductionRequest();
        request.put("title", "   ");
        JsonNode root = expectCode(post(URI, request, platform()), 400);
        assertThat(root.get("message").asText()).contains("活动名称不能为空");
    }

    @Test
    void createUnknownRuleTypeRejected() throws Exception {
        Map<String, Object> request = fullReductionRequest();
        request.put("ruleType", "BOGUS");
        expectCode(post(URI, request, platform()), 40007);
    }

    @Test
    void createInvalidDiscountRateRejected() throws Exception {
        Map<String, Object> request = fullReductionRequest();
        request.put("ruleType", "DISCOUNT");
        request.put("discountRate", 1.5);
        expectCode(post(URI, request, platform()), 40007);
    }

    @Test
    void platformShopScopeRejected() throws Exception {
        Map<String, Object> request = fullReductionRequest();
        request.put("scopeType", "SHOP");
        JsonNode root = expectCode(post(URI, request, platform()), 40007);
        assertThat(root.get("message").asText()).contains("平台活动不能使用店铺范围");
    }

    @Test
    void createInvertedTimeWindowRejected() throws Exception {
        Map<String, Object> request = fullReductionRequest();
        request.put("startTime", timeFromNow(3));
        request.put("endTime", timeFromNow(1));
        expectCode(post(URI, request, platform()), 40007);
    }

    @Test
    void createRequiresPlatformGroup() throws Exception {
        // requireGroup：无头（group=null）与 USER/MERCHANT 头一律 403
        expectCode(post(URI, fullReductionRequest()), 403);
        expectCode(post(URI, fullReductionRequest(), customer(USER_1)), 403);
        expectCode(post(URI, fullReductionRequest(), merchantA()), 403);
    }

    // ==================== 商家自建 ====================

    @Test
    void merchantCreateForcesShopScopeAndMerchantBear() throws Exception {
        // 商家请求里故意传 ALL 范围 + 平台承担 88% + 需要报名 → 全部被服务端纠正
        Map<String, Object> request = body("title", "BBT-商家促销", "ruleType", "FULL_REDUCTION",
                "thresholdAmount", 50, "discountAmount", 10,
                "scopeType", "ALL", "platformBearRate", 88, "enrollRequired", true);
        JsonNode vo = okData(post(URI + "/merchant", request, merchantA()));

        assertThat(longOf(vo, "shopId")).isEqualTo(SHOP_A);
        assertThat(vo.get("shopName").asText()).isEqualTo("黑盒店铺A");
        assertThat(vo.get("scopeType").asText()).isEqualTo("SHOP");
        assertThat(vo.get("platformBearRate").doubleValue()).isZero();
        assertThat(vo.get("enrollRequired").asBoolean()).isFalse();
        assertThat(vo.get("type").asText()).isEqualTo("店铺促销");
    }

    @Test
    void merchantProductScopeEnforcesOwnership() throws Exception {
        Map<String, Object> crossShop = body("title", "BBT-跨店商品", "ruleType", "FULL_REDUCTION",
                "discountAmount", 5, "scopeType", "PRODUCT",
                "scopeIds", List.of(PRODUCT_A1, PRODUCT_B1));
        JsonNode root = expectCode(post(URI + "/merchant", crossShop, merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("活动商品必须属于当前店铺");

        // 只圈自家商品 → 放行
        Map<String, Object> ownShop = body("title", "BBT-本店商品", "ruleType", "FULL_REDUCTION",
                "discountAmount", 5, "scopeType", "PRODUCT", "scopeIds", List.of(PRODUCT_A1));
        JsonNode vo = okData(post(URI + "/merchant", ownShop, merchantA()));
        assertThat(vo.get("scopeIds").get(0).asLong()).isEqualTo(PRODUCT_A1);
    }

    @Test
    void merchantEndpointsRequireMerchantGroup() throws Exception {
        expectCode(post(URI + "/merchant", fullReductionRequest()), 403);
        expectCode(post(URI + "/merchant", fullReductionRequest(), platform()), 403);
    }

    @Test
    void merchantCannotUpdateOrEndOtherShopActivity() throws Exception {
        long id = longOf(okData(post(URI + "/merchant", body("title", "BBT-归属校验",
                "ruleType", "FULL_REDUCTION", "discountAmount", 5), merchantA())), "id");
        // B 店商家的 PUT / end 都被拒
        JsonNode updateRoot = expectCode(put(URI + "/merchant/" + id,
                body("title", "BBT-越权改", "ruleType", "FULL_REDUCTION", "discountAmount", 9), merchantB()), 403);
        assertThat(updateRoot.get("message").asText()).contains("无权操作其他店铺的活动");
        expectCode(post(URI + "/merchant/" + id + "/end", null, merchantB()), 403);

        // A 店自己结束成功
        expectCode(post(URI + "/merchant/" + id + "/end", null, merchantA()), 200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM promotion WHERE id = ?", String.class, id)).isEqualTo("ENDED");
    }

    @Test
    void platformCannotTouchMerchantActivity() throws Exception {
        long id = longOf(okData(post(URI + "/merchant", body("title", "BBT-商家专属",
                "ruleType", "FULL_REDUCTION", "discountAmount", 5), merchantA())), "id");
        JsonNode root = expectCode(put(URI + "/" + id, body("title", "BBT-平台越权改",
                "ruleType", "FULL_REDUCTION", "discountAmount", 9), platform()), 403);
        assertThat(root.get("message").asText()).contains("商家自建活动请在商家端维护");
        expectCode(post(URI + "/" + id + "/end", null, platform()), 403);
    }

    // ==================== 平台维护 ====================

    @Test
    void platformUpdateChangesRule() throws Exception {
        long id = longOf(okData(post(URI, fullReductionRequest(), platform())), "id");
        JsonNode vo = okData(put(URI + "/" + id, body("title", "BBT-平台满减改",
                "ruleType", "FULL_REDUCTION", "thresholdAmount", 100, "discountAmount", 25,
                "scopeType", "ALL"), platform()));
        assertThat(vo.get("discountAmount").doubleValue()).isEqualTo(25);
        assertThat(longOf(vo, "id")).isEqualTo(id);
    }

    @Test
    void platformEndMarksStatusAndFiltersListing() throws Exception {
        long id = longOf(okData(post(URI, fullReductionRequest(), platform())), "id");
        expectCode(post(URI + "/" + id + "/end", null, platform()), 200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM promotion WHERE id = ?", String.class, id)).isEqualTo("ENDED");

        JsonNode activeList = okData(get(URI + "?status=active", platform()));
        assertThat(idsOf(activeList)).doesNotContain(id);
        JsonNode allList = okData(get(URI + "?status=all", platform()));
        assertThat(idsOf(allList)).contains(id);
    }

    @Test
    void unknownPromotionReturnsNotFound() throws Exception {
        expectCode(put(URI + "/99999", fullReductionRequest(), platform()), 40403);
        expectCode(post(URI + "/99999/end", null, platform()), 40403);
    }

    // ==================== 辅助 ====================

    private List<Long> idsOf(JsonNode listNode) {
        List<Long> ids = new java.util.ArrayList<>();
        listNode.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }
}
