package com.four_person.order.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.model.UserStats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrderRepository {

    PageResult<Order> pageByUser(Long userId, String status, int page, int size);

    PageResult<Order> adminPage(String status, int page, int size);

    /** 商家端：按店铺查订单（一店一单） */
    PageResult<Order> pageByShop(Long shopId, String status, int page, int size);

    Optional<Order> findByOrderNo(String orderNo);

    List<OrderItem> findItems(String orderNo);

    Map<String, List<OrderItem>> findItemsByOrderNos(List<String> orderNos);

    Order save(Order order);

    void saveItems(List<OrderItem> items);

    boolean markPaid(String orderNo, LocalDateTime time);

    boolean markShipped(String orderNo, LocalDateTime time);

    boolean markCompleted(String orderNo, LocalDateTime time);

    boolean markCancelled(String orderNo, LocalDateTime time);

    List<Long> purchasedProductIds(Long userId);

    UserStats userStats(Long userId);
}
