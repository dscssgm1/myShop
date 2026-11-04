package com.four_person.order.domain.repository;

import com.four_person.order.domain.model.OrderGroup;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderGroupRepository {

    OrderGroup save(OrderGroup group);

    Optional<OrderGroup> findByGroupNo(String groupNo);

    /** 条件更新：UNPAID -> PAID（合单支付成功回调） */
    boolean markPaid(String groupNo, LocalDateTime time);

    /** 条件更新：UNPAID -> CANCELLED（整组取消未支付订单） */
    boolean markCancelled(String groupNo, LocalDateTime time);

    /** 条件更新：PAID -> CLOSED（全部子订单取消/退款，关组并退券） */
    boolean markClosed(String groupNo, LocalDateTime time);
}
