package com.four_person.order.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderCoupon;
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

    /** 跨店合单订单组的全部子订单（含已取消/退款） */
    List<Order> findByGroupNo(String groupNo);

    List<OrderItem> findItems(String orderNo);

    Map<String, List<OrderItem>> findItemsByOrderNos(List<String> orderNos);

    /** 保存订单的优惠券核销明细（一单多券） */
    void saveCoupons(String orderNo, List<OrderCoupon> coupons);

    /** 订单实际核销的优惠券明细 */
    List<OrderCoupon> findCoupons(String orderNo);

    Order save(Order order);

    void saveItems(List<OrderItem> items);

    boolean markPaid(String orderNo, LocalDateTime time);

    /** 合单支付成功回调：组内全部待付款子订单置为已支付，返回受影响行数 */
    int markGroupChildrenPaid(String groupNo, LocalDateTime time);

    boolean markShipped(String orderNo, LocalDateTime time);

    boolean markSigned(String orderNo, LocalDateTime time);

    /** 签收已满期仍未确认收货的订单（物流签收联动自动完成） */
    List<Order> findSignedExpired(LocalDateTime deadline);

    boolean markCompleted(String orderNo, LocalDateTime time);

    boolean markCancelled(String orderNo, LocalDateTime time);

    boolean markAfterSale(String orderNo);

    boolean restoreStatus(String orderNo, String status);

    boolean markRefundedAfterCancel(String orderNo, LocalDateTime time);

    boolean markRefundedAfterSale(String orderNo, LocalDateTime time);

    /** 用户是否存在未支付的补款订单 */
    boolean hasUnpaidRecovery(Long userId);

    List<Long> purchasedProductIds(Long userId);

    UserStats userStats(Long userId);
}
