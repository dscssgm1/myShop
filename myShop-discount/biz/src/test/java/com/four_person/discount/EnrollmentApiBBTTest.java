package com.four_person.discount;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动报名黑盒：可报名列表过滤、报名校验（归属/范围/时间窗/重复）、驳回重报复位、
 * 平台审核与审核列表过滤。
 */
class EnrollmentApiBBTTest extends DiscountApiBBTSupport {

    private static final String URI = "/promotions/enrollments";

    /** 报名制平台活动：分类 7 满 100 减 20 */
    private long enrollActivity(String title, long categoryId, boolean active) throws Exception {
        return longOf(okData(post("/promotions", body(
                "title", "BBT-" + title, "type", "平台大促",
                "ruleType", "FULL_REDUCTION", "thresholdAmount", 100, "discountAmount", 20,
                "scopeType", "CATEGORY", "scopeIds", List.of(categoryId),
                "couponStackable", true, "enrollRequired", true, "platformBearRate", 50,
                "startTime", timeFromNow(active ? -1 : 1), "endTime", timeFromNow(7)), platform())), "id");
    }

    private int apply(long promotionId, List<Long> productIds) throws Exception {
        JsonNode data = okData(post(URI, body("promotionId", promotionId, "productIds", productIds), merchantA()));
        return data.asInt();
    }

    private long enrollmentId(long promotionId, long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_enrollment WHERE promotion_id = ? AND product_id = ?",
                Long.class, promotionId, productId);
    }

    // ==================== 可报名列表 ====================

    @Test
    void availableRequiresMerchantGroup() throws Exception {
        expectCode(get(URI + "/available"), 403);
        expectCode(get(URI + "/available", customer(USER_1)), 403);
        expectCode(get(URI + "/available", platform()), 403);
    }

    @Test
    void availableListsOnlyEnrollablePlatformActivities() throws Exception {
        long enrollId = enrollActivity("报名制活动", 7L, true);
        // 非报名制平台活动
        long autoId = longOf(okData(post("/promotions", body(
                "title", "BBT-全场自动活动", "ruleType", "FULL_REDUCTION",
                "thresholdAmount", 100, "discountAmount", 20, "scopeType", "ALL",
                "enrollRequired", false, "startTime", timeFromNow(-1), "endTime", timeFromNow(7)),
                platform())), "id");
        // 商家自建活动
        long merchantId = longOf(okData(post("/promotions/merchant", body(
                "title", "BBT-商家活动", "ruleType", "FULL_REDUCTION", "discountAmount", 5), merchantA())), "id");

        JsonNode available = okData(get(URI + "/available", merchantA()));
        List<Long> ids = new java.util.ArrayList<>();
        available.forEach(node -> ids.add(node.get("id").asLong()));

        assertThat(ids).contains(enrollId).doesNotContain(autoId, merchantId);
    }

    // ==================== 报名校验 ====================

    @Test
    void applyPersistsPendingEnrollment() throws Exception {
        long promotionId = enrollActivity("报名落库", 7L, true);
        int applied = apply(promotionId, List.of(PRODUCT_A1));

        assertThat(applied).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT shop_id, shop_name, product_name, status FROM promotion_enrollment WHERE id = ?",
                enrollmentId(promotionId, PRODUCT_A1));
        assertThat(((Number) row.get("shop_id")).longValue()).isEqualTo(SHOP_A);
        assertThat(row.get("shop_name")).isEqualTo("黑盒店铺A");
        assertThat(row.get("product_name")).isEqualTo("商品" + PRODUCT_A1);
        assertThat(row.get("status")).isEqualTo("PENDING");
    }

    @Test
    void applyDuplicateProductRejected() throws Exception {
        long promotionId = enrollActivity("重复报名", 7L, true);
        apply(promotionId, List.of(PRODUCT_A1));
        JsonNode root = expectCode(post(URI, body("promotionId", promotionId,
                "productIds", List.of(PRODUCT_A1)), merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("已在报名中或已通过审核");
    }

    @Test
    void applyEnforcesProductOwnership() throws Exception {
        long promotionId = enrollActivity("跨店报名", 7L, true);
        JsonNode root = expectCode(post(URI, body("promotionId", promotionId,
                "productIds", List.of(PRODUCT_B1)), merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("不属于当前店铺");
    }

    @Test
    void applyOutsideActivityScopeRejected() throws Exception {
        // 活动限定分类 8，报名分类 7 的商品
        long promotionId = enrollActivity("范围外报名", 8L, true);
        JsonNode root = expectCode(post(URI, body("promotionId", promotionId,
                "productIds", List.of(PRODUCT_A1)), merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("不在活动范围内");
    }

    @Test
    void applyUnknownProductRejected() throws Exception {
        long promotionId = enrollActivity("未知商品", 7L, true);
        JsonNode root = expectCode(post(URI, body("promotionId", promotionId,
                "productIds", List.of(999999L)), merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("商品不存在");
    }

    @Test
    void applyNonEnrollActivityRejected() throws Exception {
        long promotionId = longOf(okData(post("/promotions", body(
                "title", "BBT-免报名活动", "ruleType", "FULL_REDUCTION",
                "thresholdAmount", 100, "discountAmount", 20, "scopeType", "ALL",
                "enrollRequired", false, "startTime", timeFromNow(-1), "endTime", timeFromNow(7)),
                platform())), "id");
        JsonNode root = expectCode(post(URI, body("promotionId", promotionId,
                "productIds", List.of(PRODUCT_A1)), merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("该活动无需报名，全场自动生效");
    }

    @Test
    void applyInactivePromotionRejected() throws Exception {
        long promotionId = enrollActivity("未开始活动", 7L, false);
        JsonNode root = expectCode(post(URI, body("promotionId", promotionId,
                "productIds", List.of(PRODUCT_A1)), merchantA()), 40007);
        assertThat(root.get("message").asText()).contains("活动不在报名时间内");
    }

    @Test
    void applyUnknownPromotionRejected() throws Exception {
        expectCode(post(URI, body("promotionId", 99999L,
                "productIds", List.of(PRODUCT_A1)), merchantA()), 40403);
    }

    // ==================== 审核 ====================

    @Test
    void rejectThenReapplyResetsToPending() throws Exception {
        long promotionId = enrollActivity("驳回重报", 7L, true);
        apply(promotionId, List.of(PRODUCT_A1));
        long enrollment = enrollmentId(promotionId, PRODUCT_A1);

        Map<String, Object> rejectBody = body("reason", "商品资质不足");
        expectCode(post(URI + "/" + enrollment + "/reject", rejectBody, platform()), 200);
        Map<String, Object> rejectedRow = jdbcTemplate.queryForMap(
                "SELECT status, reason FROM promotion_enrollment WHERE id = ?", enrollment);
        assertThat(rejectedRow.get("status") + "|" + rejectedRow.get("reason"))
                .isEqualTo("REJECTED|商品资质不足");

        // 重新提交：复位为待审核、清空驳回原因、记录不变
        apply(promotionId, List.of(PRODUCT_A1));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, status, reason, reviewed_at FROM promotion_enrollment WHERE promotion_id = ? " +
                        "AND product_id = ?", promotionId, PRODUCT_A1);
        assertThat(((Number) row.get("id")).longValue()).isEqualTo(enrollment);
        assertThat(row.get("status")).isEqualTo("PENDING");
        // 驳回原因清空（实现落空串），审核时间复位
        assertThat((String) row.get("reason")).isBlank();
        assertThat(row.get("reviewed_at")).isNull();
    }

    @Test
    void rejectWithoutReasonUsesDefault() throws Exception {
        long promotionId = enrollActivity("默认驳回原因", 7L, true);
        apply(promotionId, List.of(PRODUCT_A1));
        long enrollment = enrollmentId(promotionId, PRODUCT_A1);

        expectCode(post(URI + "/" + enrollment + "/reject", null, platform()), 200);
        String reason = jdbcTemplate.queryForObject(
                "SELECT reason FROM promotion_enrollment WHERE id = ?", String.class, enrollment);
        assertThat(reason).isNotBlank();
    }

    @Test
    void approveMarksReviewedAndIsOneShot() throws Exception {
        long promotionId = enrollActivity("审核通过", 7L, true);
        apply(promotionId, List.of(PRODUCT_A1));
        long enrollment = enrollmentId(promotionId, PRODUCT_A1);

        expectCode(post(URI + "/" + enrollment + "/approve", null, platform()), 200);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_at FROM promotion_enrollment WHERE id = ?", enrollment);
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("reviewed_at")).isNotNull();

        // 已审核记录再 approve / reject → 拒绝
        expectCode(post(URI + "/" + enrollment + "/approve", null, platform()), 40007);
        expectCode(post(URI + "/" + enrollment + "/reject", null, platform()), 40007);
    }

    @Test
    void reviewRequiresPlatformGroup() throws Exception {
        long promotionId = enrollActivity("审核权限", 7L, true);
        apply(promotionId, List.of(PRODUCT_A1));
        long enrollment = enrollmentId(promotionId, PRODUCT_A1);

        expectCode(post(URI + "/" + enrollment + "/approve", null, merchantA()), 403);
        expectCode(post(URI + "/" + enrollment + "/reject", null, merchantB()), 403);
        expectCode(get(URI, merchantA()), 403);
    }

    @Test
    void unknownEnrollmentReturnsNotFound() throws Exception {
        expectCode(post(URI + "/99999/approve", null, platform()), 40403);
    }

    // ==================== 审核列表 ====================

    @Test
    void pageFiltersByShopAndStatus() throws Exception {
        // ALL 范围让两店商品都可报名，专测分页过滤
        long promotionId = longOf(okData(post("/promotions", body(
                "title", "BBT-审核分页", "ruleType", "FULL_REDUCTION",
                "thresholdAmount", 100, "discountAmount", 20, "scopeType", "ALL",
                "couponStackable", true, "enrollRequired", true, "platformBearRate", 50,
                "startTime", timeFromNow(-1), "endTime", timeFromNow(7)), platform())), "id");
        apply(promotionId, List.of(PRODUCT_A1, PRODUCT_A2)); // A 店 2 条
        long approvedEnrollment = enrollmentId(promotionId, PRODUCT_A1);
        expectCode(post(URI + "/" + approvedEnrollment + "/approve", null, platform()), 200);

        // B 店 1 条
        okData(post(URI, body("promotionId", promotionId, "productIds", List.of(PRODUCT_B1)), merchantB()));

        JsonNode shopAOnly = okData(get(URI + "?shopId=" + SHOP_A, platform()));
        assertThat(shopAOnly.get("total").asLong()).isEqualTo(2);
        assertThat(shopAOnly.get("list")).hasSize(2);

        JsonNode pending = okData(get(URI + "?status=pending", platform()));
        assertThat(pending.get("total").asLong()).isEqualTo(2);
        JsonNode approved = okData(get(URI + "?status=APPROVED", platform()));
        assertThat(approved.get("total").asLong()).isEqualTo(1);

        JsonNode byPromotion = okData(get(URI + "?promotionId=" + promotionId, platform()));
        assertThat(byPromotion.get("total").asLong()).isEqualTo(3);
    }
}
