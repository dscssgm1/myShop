package com.four_person.discount;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部核销接口黑盒（订单服务调用）：核销幂等、归属与状态校验、释放恢复。
 */
class InternalCouponApiBBTTest extends DiscountApiBBTSupport {

    private long claimTo(long userId) throws Exception {
        long templateId = longOf(okData(post("/coupons/templates", Map.of(
                "title", "BBT-核销券", "thresholdAmount", 0, "discountAmount", 10,
                "scopeType", "ALL", "totalStock", 10, "perUserLimit", 5,
                "validStart", timeFromNow(-1), "validEnd", timeFromNow(7)), platform())), "id");
        return longOf(okData(post("/coupons/" + templateId + "/claim", null, customer(userId))), "id");
    }

    private JsonNode use(long couponId, long userId, String orderNo) throws Exception {
        return okData(post("/internal/coupons/" + couponId + "/use",
                Map.of("userId", userId, "orderNo", orderNo)));
    }

    private Map<String, Object> couponRow(long couponId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, order_no, used_at FROM user_coupon WHERE id = ?", couponId);
    }

    @Test
    void useMarksCouponUsedWithOrderNo() throws Exception {
        long couponId = claimTo(USER_1);
        use(couponId, USER_1, "MS-BBT-0001");

        Map<String, Object> row = couponRow(couponId);
        assertThat(row.get("status")).isEqualTo("USED");
        assertThat(row.get("order_no")).isEqualTo("MS-BBT-0001");
        assertThat(row.get("used_at")).isNotNull();
    }

    @Test
    void useIsIdempotentForSameOrderNo() throws Exception {
        long couponId = claimTo(USER_1);
        use(couponId, USER_1, "MS-BBT-0002");
        // 同一订单重复核销 → 幂等成功
        use(couponId, USER_1, "MS-BBT-0002");
        assertThat(couponRow(couponId).get("order_no")).isEqualTo("MS-BBT-0002");
    }

    @Test
    void useWithDifferentOrderNoRejected() throws Exception {
        long couponId = claimTo(USER_1);
        use(couponId, USER_1, "MS-BBT-0003");
        expectCode(post("/internal/coupons/" + couponId + "/use",
                Map.of("userId", USER_1, "orderNo", "MS-BBT-9999")), 40004);
    }

    @Test
    void useByOtherUserRejected() throws Exception {
        long couponId = claimTo(USER_1);
        JsonNode root = expectCode(post("/internal/coupons/" + couponId + "/use",
                Map.of("userId", USER_2, "orderNo", "MS-BBT-0004")), 40404);
        assertThat(root.get("message").asText()).isNotBlank();
        // 券保持未使用
        assertThat(couponRow(couponId).get("status")).isEqualTo("UNUSED");
    }

    @Test
    void useExpiredCouponRejected() throws Exception {
        long couponId = claimTo(USER_1);
        jdbcTemplate.update("UPDATE user_coupon SET expired_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), couponId);
        expectCode(post("/internal/coupons/" + couponId + "/use",
                Map.of("userId", USER_1, "orderNo", "MS-BBT-0005")), 40003);
    }

    @Test
    void useUnknownCouponRejected() throws Exception {
        expectCode(post("/internal/coupons/99999/use",
                Map.of("userId", USER_1, "orderNo", "MS-BBT-0006")), 40404);
    }

    @Test
    void releaseRestoresUnusedAndClearsOrderNo() throws Exception {
        long couponId = claimTo(USER_1);
        use(couponId, USER_1, "MS-BBT-0007");

        okData(post("/internal/coupons/" + couponId + "/release",
                Map.of("orderNo", "MS-BBT-0007")));
        Map<String, Object> row = couponRow(couponId);
        assertThat(row.get("status")).isEqualTo("UNUSED");
        assertThat(row.get("order_no")).isNull();

        // 释放后可再次核销（换新订单号）
        use(couponId, USER_1, "MS-BBT-0008");
        assertThat(couponRow(couponId).get("order_no")).isEqualTo("MS-BBT-0008");
    }

    @Test
    void releaseIsSilentlyIdempotent() throws Exception {
        long couponId = claimTo(USER_1);
        // 未使用的券释放、订单号不匹配、券不存在 → 全部静默成功
        expectCode(post("/internal/coupons/" + couponId + "/release",
                Map.of("orderNo", "MS-BBT-NEVER")), 200);
        expectCode(post("/internal/coupons/99999/release",
                Map.of("orderNo", "MS-BBT-ANY")), 200);
        assertThat(couponRow(couponId).get("status")).isEqualTo("UNUSED");
    }

    @Test
    void claimedAndUsedCouponsVisibleInMine() throws Exception {
        long couponId = claimTo(USER_1);
        use(couponId, USER_1, "MS-BBT-0009");

        JsonNode mine = okData(get("/coupons/mine?status=USED", customer(USER_1)));
        List<Long> ids = new java.util.ArrayList<>();
        mine.forEach(node -> ids.add(node.get("id").asLong()));
        assertThat(ids).contains(couponId);
    }
}
