package com.four_person.discount.domain.service;

import com.four_person.discount.domain.model.UserPurchaseStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserSegmentationServiceTest {

    private final UserSegmentationService service = new UserSegmentationService();

    @Test
    void shouldClassifyNewUser() {
        UserSegmentationService.Segment result = service.segment(UserPurchaseStats.empty());

        assertThat(result.segment()).isEqualTo("NEW");
        assertThat(result.valueTier()).isEqualTo("LOW");
        assertThat(result.reasons()).isNotEmpty();
    }

    @Test
    void shouldClassifyHighValueActiveUser() {
        UserSegmentationService.Segment result = service.segment(
                stats(6, "8000.00", LocalDateTime.now().minusDays(5)));

        assertThat(result.segment()).isEqualTo("ACTIVE");
        assertThat(result.valueTier()).isEqualTo("HIGH");
    }

    @Test
    void shouldClassifyAtRiskUser() {
        UserSegmentationService.Segment result = service.segment(
                stats(3, "1200.00", LocalDateTime.now().minusDays(45)));

        assertThat(result.segment()).isEqualTo("AT_RISK");
        assertThat(result.valueTier()).isEqualTo("MID");
    }

    @Test
    void shouldClassifyLostUser() {
        UserSegmentationService.Segment result = service.segment(
                stats(1, "99.00", LocalDateTime.now().minusDays(120)));

        assertThat(result.segment()).isEqualTo("LOST");
        assertThat(result.valueTier()).isEqualTo("LOW");
    }

    private UserPurchaseStats stats(int orderCount, String spent, LocalDateTime lastOrderAt) {
        return new UserPurchaseStats(orderCount, new BigDecimal(spent), lastOrderAt);
    }
}
