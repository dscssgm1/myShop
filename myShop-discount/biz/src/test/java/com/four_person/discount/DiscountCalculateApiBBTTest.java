package com.four_person.discount;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 算价黑盒（端到端）：活动门槛/阶梯/折扣、券自动与指定选择、券模板叠加配置、报名收窄、
 * 承担拆分。存量活动已被基类暂停，所有断言都是精确值。
 */
class DiscountCalculateApiBBTTest extends DiscountApiBBTSupport {

    private static final String URI = "/discounts";

    // ==================== 活动优惠 ====================

    @Test
    void calculateWithoutOffersPaysFull() throws Exception {
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("originalAmount").doubleValue()).isEqualTo(99.99);
        assertThat(vo.get("promotionDiscount").doubleValue()).isZero();
        assertThat(vo.get("couponDiscount").doubleValue()).isZero();
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(99.99);
        assertThat(vo.get("merchantBearAmount").doubleValue()).isZero();
        assertThat(vo.get("platformBearAmount").doubleValue()).isZero();
    }

    @Test
    void platformFullReductionWithMerchantBear() throws Exception {
        // bearRate 0 → 商家全额承担
        createActivity("BBT-满90减20商家担", "FULL_REDUCTION", 90, 20, "ALL", null, 0, true);
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(20);
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(79.99);
        assertThat(vo.get("merchantBearAmount").doubleValue()).isEqualTo(20);
        assertThat(vo.get("platformBearAmount").doubleValue()).isZero();
    }

    @Test
    void discountRateWithBearSplitHalfUp() throws Exception {
        // 8 折 + 平台承担 50%：99.99 × 20% = 19.998 → 20.00，各担 10
        createDiscountActivity("BBT-八折对半担", 0.8, null, 50, true);
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(20.00);
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(79.99);
        assertThat(vo.get("merchantBearAmount").doubleValue()).isEqualTo(10);
        assertThat(vo.get("platformBearAmount").doubleValue()).isEqualTo(10);
    }

    @Test
    void thresholdJudgedAtExactBoundary() throws Exception {
        createActivity("BBT-门槛99.99", "FULL_REDUCTION", 99.99, 20, "ALL", null, 0, true);
        assertThat(calculate(customer(USER_1), items99_99(), null)
                .get("promotionDiscount").doubleValue()).isEqualTo(20);
        // 差一分不享受
        assertThat(calculate(customer(USER_1),
                List.of(item(PRODUCT_A1, SHOP_A, 7L, "66.66", 1), item(PRODUCT_A2, SHOP_A, 8L, "33.32", 1)), null)
                .get("promotionDiscount").doubleValue()).isZero();
    }

    @Test
    void perEveryLadderStepsAtBoundaries() throws Exception {
        // 每满 50 减 10：99.99 → 1 层，100.00 → 2 层
        createLadderActivity("BBT-每满50减10", 50, 10, true);
        assertThat(calculate(customer(USER_1), items99_99(), null)
                .get("promotionDiscount").doubleValue()).isEqualTo(10);
        assertThat(calculate(customer(USER_1),
                List.of(item(PRODUCT_A1, SHOP_A, 7L, "33.33", 2), item(PRODUCT_A2, SHOP_A, 8L, "33.34", 1)), null)
                .get("promotionDiscount").doubleValue()).isEqualTo(20);
    }

    @Test
    void categoryScopeIgnoresOtherCategoryLines() throws Exception {
        // 活动只限分类 7：A1(33.33) 参与且门槛按匹配小计判
        createActivity("BBT-分类7满30减10", "FULL_REDUCTION", 30, 10, "CATEGORY", List.of(7L), 0, true);
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(10);
    }

    @Test
    void totalsConservedAcrossShops() throws Exception {
        // 跨店铺明细按总额判门槛，优惠总额守恒
        createActivity("BBT-跨店满50减20", "FULL_REDUCTION", 50, 20, "ALL", null, 0, true);
        JsonNode vo = calculate(customer(USER_1),
                List.of(item(PRODUCT_A1, SHOP_A, 7L, "33.33", 1), item(PRODUCT_B1, SHOP_B, 7L, "20.00", 1)), null);
        assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(20);
        assertThat(vo.get("originalAmount").doubleValue()).isEqualTo(53.33);
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(33.33);
    }

    // ==================== 优惠券 ====================

    @Test
    void autoStacksUsableCouponsWithPrimaryAsLargest() throws Exception {
        long big = claimOf(USER_1, platformCoupon("BBT-满80减30", 80, 30));
        claimOf(USER_1, platformCoupon("BBT-满80减10", 80, 10));

        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        // 自动模式按上限叠多张：30 + 10 全部命中
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(40);
        assertThat(vo.get("appliedCoupons")).hasSize(2);
        // 主券 = 面额最大的一张
        assertThat(vo.get("appliedCouponId").asLong()).isEqualTo(big);
    }

    @Test
    void userSelectedCouponOverridesAuto() throws Exception {
        platformCoupon("BBT-自选30", 80, 30);
        long smallUserCoupon = claimOf(USER_1, platformCoupon("BBT-自选10", 80, 10));

        JsonNode vo = calculate(customer(USER_1), items99_99(), List.of(smallUserCoupon));
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(10);
        assertThat(vo.get("appliedCouponId").asLong()).isEqualTo(smallUserCoupon);
    }

    @Test
    void duplicateSelectedCouponCountedOnce() throws Exception {
        long id = claimOf(USER_1, platformCoupon("BBT-重复选券", 80, 30));
        JsonNode vo = calculate(customer(USER_1), items99_99(), List.of(id, id));
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(30);
        assertThat(vo.get("appliedCoupons")).hasSize(1);
    }

    @Test
    void foreignSelectedCouponRejected() throws Exception {
        long othersCoupon = claimOf(USER_2, platformCoupon("BBT-他人券", 80, 30));
        expectCode(post(URI + "/calculate", body("couponIds", List.of(othersCoupon),
                "items", items99_99()), customer(USER_1)), 40003);
    }

    @Test
    void expiredCouponSkippedByAutoAndRejectedWhenSelected() throws Exception {
        long id = claimOf(USER_1, platformCoupon("BBT-过期券", 80, 30));
        jdbcTemplate.update("UPDATE user_coupon SET expired_at = ? WHERE id = ?",
                java.time.LocalDateTime.now().minusMinutes(1), id);

        // 自动选择跳过过期券
        assertThat(calculate(customer(USER_1), items99_99(), null).get("couponDiscount").doubleValue()).isZero();
        // 显式指定 → 拒绝
        expectCode(post(URI + "/calculate", body("couponIds", List.of(id),
                "items", items99_99()), customer(USER_1)), 40003);
    }

    @Test
    void couponCappedAtOriginalWhenLarger() throws Exception {
        platformCoupon("BBT-大额券", 10, 30);
        long id = claimOf(USER_1, findTemplateByTitle("BBT-大额券"));
        JsonNode vo = calculate(customer(USER_1),
                List.of(item(PRODUCT_A1, SHOP_A, 7L, "20.00", 1)), List.of(id));
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(20);
        assertThat(vo.get("payAmount").doubleValue()).isZero();
    }

    // ==================== 叠加规则 ====================

    @Test
    void stackablePromotionPlusCoupon() throws Exception {
        createActivity("BBT-可叠满90减20", "FULL_REDUCTION", 90, 20, "ALL", null, 50, true);
        long coupon = claimOf(USER_1, platformCoupon("BBT-可叠满80减30", 80, 30));

        JsonNode vo = calculate(customer(USER_1), items99_99(), List.of(coupon));
        assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(20);
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(30);
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(49.99);
        // 活动优惠 20 五五分（平台 10 / 商家 10）；平台券面额 30 全额记平台承担
        assertThat(vo.get("platformBearAmount").doubleValue()).isEqualTo(40);
        assertThat(vo.get("merchantBearAmount").doubleValue()).isEqualTo(10);
        assertThat(vo.get("promotionCouponStackable").asBoolean()).isTrue();
    }

    @Test
    void stackingConflictAutoPicksBetterSide() throws Exception {
        // 活动 20 < 券 30 → 自动模式静默留券
        createActivity("BBT-不可叠20", "FULL_REDUCTION", 90, 20, "ALL", null, 0, false);
        long coupon = claimOf(USER_1, platformCoupon("BBT-券30", 80, 30));
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(30);
        assertThat(vo.get("promotionDiscount").doubleValue()).isZero();
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(69.99);
        assertThat(vo.get("promotionCouponStackable").asBoolean()).isFalse();

        // 反过来活动 40 > 券 30 → 留活动
        createActivity("BBT-不可叠40", "FULL_REDUCTION", 60, 40, "ALL", null, 0, false);
        JsonNode vo2 = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo2.get("promotionDiscount").doubleValue()).isEqualTo(40);
        assertThat(vo2.get("couponDiscount").doubleValue()).isZero();
        assertThat(vo2.get("payAmount").doubleValue()).isEqualTo(59.99);
    }

    @Test
    void stackingConflictSelectedErrors() throws Exception {
        // 指定模式冲突直接报错：活动侧不允许叠券
        createActivity("BBT-不可叠选券", "FULL_REDUCTION", 90, 20, "ALL", null, 0, false);
        long coupon = claimOf(USER_1, platformCoupon("BBT-指定券30", 80, 30));
        JsonNode root = expectCode(post(URI + "/calculate", body("couponIds", List.of(coupon),
                "items", items99_99()), customer(USER_1)), 40003);
        assertThat(root.get("message").asText()).contains("当前促销活动不可与优惠券叠加使用");
    }

    @Test
    void couponNotStackableWithActivityEndToEnd() throws Exception {
        // 券模板配置「不可与活动同用」：自动模式静默二选一，指定模式报错
        long template = longOf(okData(post("/coupons/templates", body(
                "title", "BBT-不可与活动同用券", "thresholdAmount", 80, "discountAmount", 30,
                "scopeType", "ALL", "totalStock", 100, "perUserLimit", 5,
                "couponStackable", false,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), platform())), "id");
        long coupon = claimOf(USER_1, template);
        createActivity("BBT-可叠20", "FULL_REDUCTION", 90, 20, "ALL", null, 50, true);

        // 自动：券 30 > 活动 20 → 仅用券
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(30);
        assertThat(vo.get("promotionDiscount").doubleValue()).isZero();

        // 指定：冲突报错
        JsonNode root = expectCode(post(URI + "/calculate", body("couponIds", List.of(coupon),
                "items", items99_99()), customer(USER_1)), 40003);
        assertThat(root.get("message").asText()).contains("不可与促销活动叠加使用");
    }

    @Test
    void nonStackableCouponCannotCombineWithOthers() throws Exception {
        // 券模板配置「不可与其他券叠加」：自动模式取最优单张，指定多张直接报错
        long template = longOf(okData(post("/coupons/templates", body(
                "title", "BBT-不可叠加券30", "thresholdAmount", 80, "discountAmount", 30,
                "scopeType", "ALL", "totalStock", 100, "perUserLimit", 5,
                "stackable", false,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), platform())), "id");
        long solo = claimOf(USER_1, template);
        long other = claimOf(USER_1, platformCoupon("BBT-可叠券10", 80, 10));

        // 自动：30 > 10 → 单独用不可叠加券
        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(30);
        assertThat(vo.get("appliedCoupons")).hasSize(1);
        assertThat(vo.get("appliedCouponId").asLong()).isEqualTo(solo);

        // 指定两张 → 报错
        JsonNode root = expectCode(post(URI + "/calculate", body("couponIds", List.of(solo, other),
                "items", items99_99()), customer(USER_1)), 40003);
        assertThat(root.get("message").asText()).contains("不可与其他优惠券叠加使用");

        // 指定一张 → 正常
        JsonNode single = calculate(customer(USER_1), items99_99(), List.of(solo));
        assertThat(single.get("couponDiscount").doubleValue()).isEqualTo(30);
    }

    @Test
    void maxCouponsPerOrderLimitIsThree() throws Exception {
        platformCoupon("BBT-多券30", 80, 30);
        platformCoupon("BBT-多券10", 80, 10);
        long big = claimOf(USER_1, findTemplateByTitle("BBT-多券30"));
        long small = claimOf(USER_1, findTemplateByTitle("BBT-多券10"));

        // 指定两张全用
        JsonNode vo = calculate(customer(USER_1), items99_99(), List.of(big, small));
        assertThat(vo.get("couponDiscount").doubleValue()).isEqualTo(40);
        assertThat(vo.get("appliedCoupons")).hasSize(2);

        // 指定超出上限 3 张 → 直接拒绝
        platformCoupon("BBT-多券5", 80, 5);
        platformCoupon("BBT-多券1", 80, 1);
        long third = claimOf(USER_1, findTemplateByTitle("BBT-多券5"));
        long fourth = claimOf(USER_1, findTemplateByTitle("BBT-多券1"));
        JsonNode root = expectCode(post(URI + "/calculate", body("couponIds", List.of(big, small, third, fourth),
                "items", items99_99()), customer(USER_1)), 40003);
        assertThat(root.get("message").asText()).contains("单笔订单最多叠加 3 张优惠券");
    }

    // ==================== 报名收窄端到端 ====================

    @Test
    void enrollmentNarrowsPromotionToApprovedProducts() throws Exception {
        // 报名制活动：分类 7 满 30 减 10
        long promotionId = longOf(okData(post("/promotions", body(
                "title", "BBT-报名收窄", "ruleType", "FULL_REDUCTION",
                "thresholdAmount", 30, "discountAmount", 10,
                "scopeType", "CATEGORY", "scopeIds", List.of(7L),
                "couponStackable", true, "enrollRequired", true, "platformBearRate", 50,
                "startTime", timeFromNow(-1), "endTime", timeFromNow(7)), platform())), "id");

        // 未报名：无优惠
        assertThat(calculate(customer(USER_1), items99_99(), null)
                .get("promotionDiscount").doubleValue()).isZero();

        // 报名待审核：仍无优惠
        okData(post("/promotions/enrollments", body("promotionId", promotionId,
                "productIds", List.of(PRODUCT_A1)), merchantA()));
        assertThat(calculate(customer(USER_1), items99_99(), null)
                .get("promotionDiscount").doubleValue()).isZero();

        // 审核通过：A1（分类 7）享受活动，A2（分类 8）不参与
        long enrollment = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_enrollment WHERE promotion_id = ? AND product_id = ?",
                Long.class, promotionId, PRODUCT_A1);
        okData(post("/promotions/enrollments/" + enrollment + "/approve", null, platform()));

        JsonNode vo = calculate(customer(USER_1), items99_99(), null);
        assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(10);
        assertThat(vo.get("appliedPromotionId").asLong()).isEqualTo(promotionId);
        assertThat(vo.get("payAmount").doubleValue()).isEqualTo(89.99);
    }

    // ==================== 结算页可用券 ====================

    @Test
    void availableCouponsReportApplicableAmounts() throws Exception {
        platformCoupon("BBT-可用满80减30", 80, 30);
        platformCoupon("BBT-可用满200减50", 200, 50);
        claimOf(USER_1, findTemplateByTitle("BBT-可用满80减30"));
        claimOf(USER_1, findTemplateByTitle("BBT-可用满200减50"));

        JsonNode list = okData(post(URI + "/available-coupons", body("items", items99_99()), customer(USER_1)));
        assertThat(list).hasSize(2);
        Map<Long, Double> amounts = new java.util.HashMap<>();
        list.forEach(node -> amounts.put(node.get("templateId").asLong(), node.get("applicableAmount").doubleValue()));
        double small = amounts.get(findTemplateByTitle("BBT-可用满80减30"));
        double big = amounts.get(findTemplateByTitle("BBT-可用满200减50"));
        assertThat(small).isEqualTo(30);
        assertThat(big).isZero(); // 门槛不满足 → 前端禁用

        // 未登录 → 401
        mockMvc.perform(MockMvcRequestBuilders.post(URI + "/available-coupons")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items99_99()))))
                .andExpect(result -> assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsString()).get("code").asInt()).isEqualTo(401));
    }

    // ==================== 校验 ====================

    @Test
    void calculateRejectsEmptyItems() throws Exception {
        JsonNode root = expectCode(post(URI + "/calculate", body("items", List.of()), customer(USER_1)), 400);
        assertThat(root.get("message").asText()).contains("商品明细不能为空");
    }

    @Test
    void calculateWithoutUserHeaderRejected() throws Exception {
        createActivity("BBT-无头算价", "FULL_REDUCTION", 50, 20, "ALL", null, 0, true);
        // 无 userId 请求体且无身份头 → 401
        mockMvc.perform(MockMvcRequestBuilders.post(URI + "/calculate")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items99_99()))))
                .andExpect(result -> assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsString()).get("code").asInt()).isEqualTo(401));

        // 请求体显式带 userId（订单服务内部调用形态）→ 正常算价
        mockMvc.perform(MockMvcRequestBuilders.post(URI + "/calculate")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", USER_1,
                                "items", items99_99()))))
                .andExpect(result -> {
                    JsonNode vo = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
                    assertThat(vo.get("promotionDiscount").doubleValue()).isEqualTo(20);
                    assertThat(vo.get("couponDiscount").doubleValue()).isZero();
                });
    }

    // ==================== 辅助 ====================

    private List<Map<String, Object>> items99_99() {
        return List.of(
                item(PRODUCT_A1, SHOP_A, 7L, "33.33", 1),
                item(PRODUCT_A2, SHOP_A, 8L, "66.66", 1));
    }

    private JsonNode calculate(RequestPostProcessor user, List<Map<String, Object>> items,
                               List<Long> couponIds) throws Exception {
        return okData(post(URI + "/calculate", body("couponIds", couponIds, "items", items), user));
    }

    private long createActivity(String title, String ruleType, double threshold, double amount,
                                String scopeType, List<Long> scopeIds, int bearRate, boolean stackable)
            throws Exception {
        return longOf(okData(post("/promotions", body(
                "title", title, "type", "平台大促", "ruleType", ruleType,
                "thresholdAmount", threshold, "discountAmount", amount,
                "scopeType", scopeType, "scopeIds", scopeIds,
                "couponStackable", stackable, "enrollRequired", false,
                "platformBearRate", bearRate,
                "startTime", timeFromNow(-1), "endTime", timeFromNow(7)), platform())), "id");
    }

    private long createDiscountActivity(String title, double rate, Double maxDiscount,
                                        int bearRate, boolean stackable) throws Exception {
        return longOf(okData(post("/promotions", body(
                "title", title, "type", "平台大促", "ruleType", "DISCOUNT",
                "discountRate", rate, "maxDiscount", maxDiscount,
                "scopeType", "ALL", "couponStackable", stackable, "enrollRequired", false,
                "platformBearRate", bearRate,
                "startTime", timeFromNow(-1), "endTime", timeFromNow(7)), platform())), "id");
    }

    private long createLadderActivity(String title, int perEvery, double amount, boolean stackable)
            throws Exception {
        return longOf(okData(post("/promotions", body(
                "title", title, "type", "平台大促", "ruleType", "FULL_REDUCTION",
                "perEvery", perEvery, "discountAmount", amount,
                "scopeType", "ALL", "couponStackable", stackable, "enrollRequired", false,
                "platformBearRate", 0,
                "startTime", timeFromNow(-1), "endTime", timeFromNow(7)), platform())), "id");
    }

    private long platformCoupon(String title, double threshold, double amount) throws Exception {
        return longOf(okData(post("/coupons/templates", body(
                "title", title, "thresholdAmount", threshold, "discountAmount", amount,
                "scopeType", "ALL", "totalStock", 100, "perUserLimit", 5,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), platform())), "id");
    }

    private long claimOf(long userId, long templateId) throws Exception {
        return longOf(okData(post("/coupons/" + templateId + "/claim", null, customer(userId))), "id");
    }

    private long findTemplateByTitle(String title) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM coupon_template WHERE title = ?", Long.class, title);
    }
}
