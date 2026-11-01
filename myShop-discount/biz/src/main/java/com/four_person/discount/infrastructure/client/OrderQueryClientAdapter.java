package com.four_person.discount.infrastructure.client;

import com.four_person.common.api.Results;
import com.four_person.discount.application.port.OrderQueryPort;
import com.four_person.discount.domain.model.UserPurchaseStats;
import com.four_person.order.api.OrderInternalApi;
import com.four_person.order.api.dto.UserStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderQueryClientAdapter implements OrderQueryPort {

    private final OrderInternalApi orderInternalApi;

    @Override
    public List<Long> purchasedProductIds(Long userId) {
        List<Long> ids = Results.data(orderInternalApi.purchasedProducts(userId));
        return ids == null ? List.of() : ids;
    }

    @Override
    public UserPurchaseStats stats(Long userId) {
        UserStatsVO stats = Results.data(orderInternalApi.stats(userId));
        if (stats == null) {
            return UserPurchaseStats.empty();
        }
        return new UserPurchaseStats(
                stats.getOrderCount() == null ? 0 : stats.getOrderCount(),
                stats.getTotalSpent() == null ? BigDecimal.ZERO : stats.getTotalSpent(),
                stats.getLastOrderAt());
    }
}
