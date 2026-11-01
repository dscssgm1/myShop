package com.four_person.order.application;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.discount.api.DiscountInternalApi;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.DiscountCalculateRequest;
import com.four_person.discount.api.dto.DiscountItem;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.order.api.dto.CreateOrderVO;
import com.four_person.order.api.dto.OrderCreateRequest;
import com.four_person.order.api.dto.OrderInternalDTO;
import com.four_person.order.api.dto.OrderItemRequest;
import com.four_person.order.api.dto.OrderItemVO;
import com.four_person.order.api.dto.OrderPreviewItemVO;
import com.four_person.order.api.dto.OrderPreviewRequest;
import com.four_person.order.api.dto.OrderPreviewVO;
import com.four_person.order.api.dto.OrderVO;
import com.four_person.order.api.dto.PaidRequest;
import com.four_person.order.api.dto.UserStatsVO;
import com.four_person.order.api.enums.OrderStatus;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.model.UserStats;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.order.infrastructure.support.OrderNoGenerator;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockChangeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal FREIGHT_AMOUNT = ZERO;

    private final OrderRepository orderRepository;
    private final ProductInternalApi productInternalApi;
    private final DiscountInternalApi discountInternalApi;
    private final OrderNoGenerator orderNoGenerator;

    public OrderPreviewVO preview(Long userId, OrderPreviewRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponId());
        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setItems(prepared.items.stream().map(this::toPreviewItemVO).toList());
        vo.setOriginalAmount(prepared.originalAmount);
        vo.setPromotionDiscount(prepared.discount.getPromotionDiscount());
        vo.setCouponDiscount(prepared.discount.getCouponDiscount());
        vo.setTotalDiscount(prepared.discount.getTotalDiscount());
        vo.setFreightAmount(FREIGHT_AMOUNT);
        vo.setPayAmount(prepared.discount.getPayAmount());
        vo.setAppliedPromotionId(prepared.discount.getAppliedPromotionId());
        vo.setAppliedPromotionTitle(prepared.discount.getAppliedPromotionTitle());
        vo.setAppliedCouponId(prepared.discount.getAppliedCouponId());
        vo.setAppliedCouponTitle(prepared.discount.getAppliedCouponTitle());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO create(Long userId, OrderCreateRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponId());
        String orderNo = orderNoGenerator.next();
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStatus(OrderStatus.UNPAID.name());
        order.setOriginalAmount(prepared.originalAmount);
        order.setPromotionDiscount(prepared.discount.getPromotionDiscount());
        order.setCouponDiscount(prepared.discount.getCouponDiscount());
        order.setFreightAmount(FREIGHT_AMOUNT);
        order.setPayAmount(prepared.discount.getPayAmount());
        order.setPromotionId(prepared.discount.getAppliedPromotionId());
        order.setPromotionTitle(prepared.discount.getAppliedPromotionTitle());
        order.setCouponId(prepared.discount.getAppliedCouponId());
        order.setCouponTitle(prepared.discount.getAppliedCouponTitle());
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setRemark(request.getRemark());
        orderRepository.save(order);

        List<OrderItem> items = prepared.items.stream()
                .map(item -> toOrderItem(order, item))
                .toList();
        orderRepository.saveItems(items);

        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setItems(prepared.stockItems);
        Results.data(productInternalApi.deductStock(stockRequest));

        if (prepared.discount.getAppliedCouponId() != null) {
            try {
                CouponUseRequest useRequest = new CouponUseRequest();
                useRequest.setUserId(userId);
                useRequest.setOrderNo(orderNo);
                Results.data(discountInternalApi.useCoupon(prepared.discount.getAppliedCouponId(), useRequest));
            } catch (RuntimeException ex) {
                log.warn("订单 {} 核销优惠券失败，回滚库存", orderNo, ex);
                try {
                    Results.data(productInternalApi.restoreStock(stockRequest));
                } catch (Exception restoreEx) {
                    log.error("订单 {} 回滚库存失败，需要人工处理", orderNo, restoreEx);
                }
                throw ex;
            }
        }
        return new CreateOrderVO(orderNo, order.getPayAmount());
    }

    public PageResult<OrderVO> page(Long userId, String status, int page, int size) {
        PageResult<Order> result = orderRepository.pageByUser(userId, status, page, size);
        return assemblePage(result);
    }

    public PageResult<OrderVO> adminPage(String status, int page, int size) {
        PageResult<Order> result = orderRepository.adminPage(status, page, size);
        return assemblePage(result);
    }

    public OrderVO detail(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        return toVO(order, orderRepository.findItems(orderNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        LocalDateTime now = LocalDateTime.now();
        order.cancel(now);
        if (!orderRepository.markCancelled(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
        List<OrderItem> items = orderRepository.findItems(orderNo);
        Results.data(productInternalApi.restoreStock(toStockRequest(items)));
        if (order.getCouponId() != null) {
            CouponReleaseRequest releaseRequest = new CouponReleaseRequest();
            releaseRequest.setOrderNo(orderNo);
            Results.data(discountInternalApi.releaseCoupon(order.getCouponId(), releaseRequest));
        }
    }

    public void confirm(Long userId, String orderNo) {
        Order order = requireOwned(userId, orderNo);
        LocalDateTime now = LocalDateTime.now();
        order.complete(now);
        if (!orderRepository.markCompleted(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
    }

    public void ship(String orderNo) {
        Order order = requireOrder(orderNo);
        LocalDateTime now = LocalDateTime.now();
        order.ship(now);
        if (!orderRepository.markShipped(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单状态已变化，请刷新后重试");
        }
    }

    public OrderInternalDTO getInternal(String orderNo) {
        Order order = requireOrder(orderNo);
        OrderInternalDTO dto = new OrderInternalDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setStatus(order.getStatus());
        dto.setPayAmount(order.getPayAmount());
        return dto;
    }

    public void markPaid(String orderNo, PaidRequest request) {
        Order order = requireOrder(orderNo);
        if (order.isPaid()) {
            log.info("订单 {} 已支付，忽略重复回调", orderNo);
            return;
        }
        LocalDateTime paidAt = request.getPaidAt() == null ? LocalDateTime.now() : request.getPaidAt();
        order.pay(paidAt);
        if (!orderRepository.markPaid(orderNo, paidAt)) {
            Order latest = requireOrder(orderNo);
            if (latest.isPaid()) {
                return;
            }
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "订单当前状态不能支付");
        }
    }

    public List<Long> purchasedProductIds(Long userId) {
        return orderRepository.purchasedProductIds(userId);
    }

    public UserStatsVO userStats(Long userId) {
        UserStats stats = orderRepository.userStats(userId);
        UserStatsVO vo = new UserStatsVO();
        vo.setOrderCount(stats.orderCount());
        vo.setTotalSpent(stats.totalSpent());
        vo.setLastOrderAt(stats.lastOrderAt());
        return vo;
    }

    private PreparedOrder prepare(Long userId, List<OrderItemRequest> itemRequests, Long couponId) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : itemRequests) {
            merged.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        ProductBatchRequest batchRequest = new ProductBatchRequest();
        batchRequest.setIds(new ArrayList<>(merged.keySet()));
        List<ProductVO> products = Results.data(productInternalApi.batch(batchRequest));
        Map<Long, ProductVO> productMap = products == null ? Collections.emptyMap()
                : products.stream().collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        List<PreparedItem> items = new ArrayList<>();
        List<DiscountItem> discountItems = new ArrayList<>();
        List<StockChangeRequest.StockItem> stockItems = new ArrayList<>();
        BigDecimal originalAmount = ZERO;

        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            ProductVO product = productMap.get(entry.getKey());
            if (product == null) {
                throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND, "商品 " + entry.getKey() + " 不存在");
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BusinessException(OrderErrorCode.PRODUCT_OFF_SHELF, product.getName() + " 已下架");
            }
            if (product.getStock() == null || product.getStock() < entry.getValue()) {
                throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, product.getName() + " 库存不足");
            }
            BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(entry.getValue()))
                    .setScale(2, RoundingMode.HALF_UP);
            originalAmount = originalAmount.add(subtotal);
            items.add(new PreparedItem(product, entry.getValue(), subtotal));

            DiscountItem discountItem = new DiscountItem();
            discountItem.setProductId(product.getId());
            discountItem.setCategoryId(product.getCategoryId());
            discountItem.setPrice(product.getPrice());
            discountItem.setQuantity(entry.getValue());
            discountItems.add(discountItem);

            StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
            stockItem.setProductId(product.getId());
            stockItem.setQuantity(entry.getValue());
            stockItems.add(stockItem);
        }

        DiscountCalculateRequest discountRequest = new DiscountCalculateRequest();
        discountRequest.setUserId(userId);
        discountRequest.setCouponId(couponId);
        discountRequest.setItems(discountItems);
        DiscountResultVO discount = Results.data(discountInternalApi.calculate(discountRequest));

        return new PreparedOrder(items, originalAmount, discount, stockItems);
    }

    private PageResult<OrderVO> assemblePage(PageResult<Order> result) {
        Map<String, List<OrderItem>> itemMap = orderRepository.findItemsByOrderNos(
                result.getList().stream().map(Order::getOrderNo).toList());
        List<OrderVO> records = result.getList().stream()
                .map(order -> toVO(order, itemMap.getOrDefault(order.getOrderNo(), List.of())))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
    }

    private Order requireOwned(Long userId, String orderNo) {
        Order order = requireOrder(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        return order;
    }

    private Order requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private StockChangeRequest toStockRequest(List<OrderItem> items) {
        StockChangeRequest request = new StockChangeRequest();
        request.setItems(items.stream().map(item -> {
            StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
            stockItem.setProductId(item.getProductId());
            stockItem.setQuantity(item.getQuantity());
            return stockItem;
        }).toList());
        return request;
    }

    private OrderPreviewItemVO toPreviewItemVO(PreparedItem item) {
        OrderPreviewItemVO vo = new OrderPreviewItemVO();
        vo.setProductId(item.product.getId());
        vo.setProductName(item.product.getName());
        vo.setProductEmoji(item.product.getEmoji());
        vo.setProductColor(item.product.getColor());
        vo.setShopId(item.product.getShopId());
        vo.setShopName(item.product.getShopName());
        vo.setPrice(item.product.getPrice());
        vo.setQuantity(item.quantity);
        vo.setSubtotal(item.subtotal);
        return vo;
    }

    private OrderItem toOrderItem(Order order, PreparedItem item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setOrderNo(order.getOrderNo());
        orderItem.setProductId(item.product.getId());
        orderItem.setProductName(item.product.getName());
        orderItem.setProductEmoji(item.product.getEmoji());
        orderItem.setProductColor(item.product.getColor());
        orderItem.setShopId(item.product.getShopId());
        orderItem.setShopName(item.product.getShopName());
        orderItem.setPrice(item.product.getPrice());
        orderItem.setQuantity(item.quantity);
        orderItem.setSubtotal(item.subtotal);
        return orderItem;
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.textOf(order.getStatus()));
        vo.setOriginalAmount(order.getOriginalAmount());
        vo.setPromotionDiscount(order.getPromotionDiscount());
        vo.setCouponDiscount(order.getCouponDiscount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setPromotionTitle(order.getPromotionTitle());
        vo.setCouponTitle(order.getCouponTitle());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        vo.setCompleteTime(order.getCompleteTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductEmoji(item.getProductEmoji());
        vo.setProductColor(item.getProductColor());
        vo.setShopId(item.getShopId());
        vo.setShopName(item.getShopName());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }

    private record PreparedItem(ProductVO product, int quantity, BigDecimal subtotal) {
    }

    private record PreparedOrder(List<PreparedItem> items,
                                 BigDecimal originalAmount,
                                 DiscountResultVO discount,
                                 List<StockChangeRequest.StockItem> stockItems) {
    }
}
