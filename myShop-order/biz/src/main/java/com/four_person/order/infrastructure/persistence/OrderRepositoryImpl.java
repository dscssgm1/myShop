package com.four_person.order.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.model.UserStats;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.infrastructure.persistence.mapper.OrderItemMapper;
import com.four_person.order.infrastructure.persistence.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public PageResult<Order> pageByUser(Long userId, String status, int page, int size) {
        return doPage(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .eq(StringUtils.hasText(status), Order::getStatus, status)
                .orderByDesc(Order::getId), page, size);
    }

    @Override
    public PageResult<Order> adminPage(String status, int page, int size) {
        return doPage(Wrappers.<Order>lambdaQuery()
                .eq(StringUtils.hasText(status), Order::getStatus, status)
                .orderByDesc(Order::getId), page, size);
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        return Optional.ofNullable(orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo)));
    }

    @Override
    public List<OrderItem> findItems(String orderNo) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderNo, orderNo)
                .orderByAsc(OrderItem::getId));
    }

    @Override
    public Map<String, List<OrderItem>> findItemsByOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderNo, orderNos)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderNo));
    }

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
        return order;
    }

    @Override
    public void saveItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            orderItemMapper.insert(item);
        }
    }

    @Override
    public boolean markPaid(String orderNo, LocalDateTime time) {
        return orderMapper.markPaid(orderNo, time) > 0;
    }

    @Override
    public boolean markShipped(String orderNo, LocalDateTime time) {
        return orderMapper.markShipped(orderNo, time) > 0;
    }

    @Override
    public boolean markCompleted(String orderNo, LocalDateTime time) {
        return orderMapper.markCompleted(orderNo, time) > 0;
    }

    @Override
    public boolean markCancelled(String orderNo, LocalDateTime time) {
        return orderMapper.markCancelled(orderNo, time) > 0;
    }

    @Override
    public List<Long> purchasedProductIds(Long userId) {
        List<Order> orders = paidOrders(userId, 100);
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> orderNos = orders.stream().map(Order::getOrderNo).toList();
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderNo, orderNos))
                .stream()
                .map(OrderItem::getProductId)
                .distinct()
                .limit(100)
                .toList();
    }

    @Override
    public UserStats userStats(Long userId) {
        List<Order> orders = paidOrders(userId, Integer.MAX_VALUE);
        BigDecimal totalSpent = orders.stream()
                .map(Order::getPayAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        LocalDateTime lastOrderAt = orders.stream()
                .map(Order::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new UserStats(orders.size(), totalSpent, lastOrderAt);
    }

    private List<Order> paidOrders(Long userId, int limit) {
        LambdaQueryWrapper<Order> wrapper = Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .in(Order::getStatus, OrderStatus.PAID.name(), OrderStatus.SHIPPED.name(), OrderStatus.COMPLETED.name())
                .orderByDesc(Order::getId);
        if (limit < Integer.MAX_VALUE) {
            wrapper.last("LIMIT " + limit);
        }
        return orderMapper.selectList(wrapper);
    }

    private PageResult<Order> doPage(LambdaQueryWrapper<Order> wrapper, int pageNo, int size) {
        int pageIndex = Math.max(pageNo, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<Order> page = orderMapper.selectPage(new Page<>(pageIndex, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageIndex, pageSize);
    }
}
