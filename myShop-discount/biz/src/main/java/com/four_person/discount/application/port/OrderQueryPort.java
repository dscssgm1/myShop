package com.four_person.discount.application.port;

import com.four_person.discount.domain.model.UserPurchaseStats;

import java.util.List;

/**
 * 订单查询端口，基础设施层用订单服务 Feign 契约实现。
 */
public interface OrderQueryPort {

    List<Long> purchasedProductIds(Long userId);

    UserPurchaseStats stats(Long userId);
}
