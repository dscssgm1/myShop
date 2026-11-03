package com.four_person.discount.domain.model;

import com.four_person.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 报名记录状态机：报名 → 审核通过/驳回 → 重新报名复位。
 */
class PromotionEnrollmentTest {

    @Test
    void shouldRejectIncompleteKeyFields() {
        assertThatThrownBy(() -> new PromotionEnrollment(1L, null, 100L, "店", 1L, "商品", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报名信息不完整");
        assertThatThrownBy(() -> new PromotionEnrollment(1L, 1L, null, "店", 1L, "商品", null, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new PromotionEnrollment(1L, 1L, 100L, "店", null, "商品", null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyStartsPendingWithDefaults() {
        PromotionEnrollment record = PromotionEnrollment.apply(1L, 100L, "测试店", 7L, "测试商品");
        assertThat(record.getStatus()).isEqualTo(PromotionEnrollment.STATUS_PENDING);
        assertThat(record.getAppliedAt()).isNotNull();
        assertThat(record.getReviewedAt()).isNull();
        assertThat(record.getReason()).isEmpty();
        assertThat(record.getShopName()).isEqualTo("测试店");
    }

    @Test
    void nullNamesAndStatusFallBackToDefaults() {
        PromotionEnrollment record = new PromotionEnrollment(1L, 1L, 100L, null, 7L, null, null, null, null, null);
        assertThat(record.getShopName()).isEmpty();
        assertThat(record.getProductName()).isEmpty();
        assertThat(record.getStatus()).isEqualTo(PromotionEnrollment.STATUS_PENDING);
        assertThat(record.getReason()).isEmpty();
    }

    @Test
    void approveSetsStatusAndReviewTime() {
        PromotionEnrollment approved = PromotionEnrollment.apply(1L, 100L, "店", 7L, "商品").approved();
        assertThat(approved.getStatus()).isEqualTo(PromotionEnrollment.STATUS_APPROVED);
        assertThat(approved.getReason()).isEmpty();
        assertThat(approved.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectKeepsCustomReasonOrDefaults() {
        PromotionEnrollment rejected = PromotionEnrollment.apply(1L, 100L, "店", 7L, "商品").rejected("商品不活动");
        assertThat(rejected.getStatus()).isEqualTo(PromotionEnrollment.STATUS_REJECTED);
        assertThat(rejected.getReason()).isEqualTo("商品不活动");
        assertThat(rejected.getReviewedAt()).isNotNull();

        PromotionEnrollment blankReason = PromotionEnrollment.apply(1L, 100L, "店", 7L, "商品").rejected("  ");
        assertThat(blankReason.getReason()).isEqualTo("不符合活动要求");
    }

    @Test
    void reappliedResetsToPendingAndClearsReason() {
        PromotionEnrollment original = PromotionEnrollment.apply(1L, 100L, "店", 7L, "商品").rejected("不符合");
        PromotionEnrollment reapplied = original.reapplied();

        assertThat(reapplied.getId()).isEqualTo(original.getId());
        assertThat(reapplied.getStatus()).isEqualTo(PromotionEnrollment.STATUS_PENDING);
        assertThat(reapplied.getReason()).isEmpty();
        assertThat(reapplied.getReviewedAt()).isNull();
        assertThat(reapplied.getAppliedAt()).isNotNull();
        // 报名记录键不变：活动 × 商品
        assertThat(reapplied.getPromotionId()).isEqualTo(1L);
        assertThat(reapplied.getProductId()).isEqualTo(7L);
    }
}
