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

/**
 * 订单应用服务：下单时按店铺拆分，每个店铺生成一个独立订单（一店一单）。
 */
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

    // ==================== 预览 ====================

    public OrderPreviewVO preview(Long userId, OrderPreviewRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponId());
        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setItems(prepared.allItems.stream().map(this::toPreviewItemVO).toList());
        vo.setShops(buildShopPreviews(prepared));
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

    // ==================== 创建订单（一店一单） ====================

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderVO create(Long userId, OrderCreateRequest request) {
        PreparedOrder prepared = prepare(userId, request.getItems(), request.getCouponId());
        List<AllocatedShop> allocations = allocateShops(prepared);

        if (prepared.discount.getAppliedCouponId() != null && allocations.size() > 1) {
            throw new BusinessException(40010, "跨店铺下单暂不支持使用优惠券，请分店铺结算");
        }

        List<CreateOrderVO.OrderBrief> briefs = new ArrayList<>();
        for (AllocatedShop allocated : allocations) {
            String orderNo = orderNoGenerator.next();
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setShopId(allocated.shopId);
            order.setShopName(allocated.shopName);
            order.setStatus(OrderStatus.UNPAID.name());
            order.setOriginalAmount(allocated.originalAmount);
            order.setPromotionDiscount(allocated.promotionDiscount);
            order.setCouponDiscount(allocated.couponDiscount);
            order.setFreightAmount(FREIGHT_AMOUNT);
            order.setPayAmount(allocated.payAmount);
            order.setPromotionId(prepared.discount.getAppliedPromotionId());
            order.setPromotionTitle(prepared.discount.getAppliedPromotionTitle());
            order.setCouponId(prepared.discount.getAppliedCouponId());
            order.setCouponTitle(prepared.discount.getAppliedCouponTitle());
            order.setReceiverName(request.getReceiverName());
            order.setReceiverPhone(request.getReceiverPhone());
            order.setReceiverAddress(request.getReceiverAddress());
            order.setRemark(request.getRemark());
            orderRepository.save(order);

            List<OrderItem> items = allocated.items.stream()
                    .map(item -> toOrderItem(order, item))
                    .toList();
            orderRepository.saveItems(items);

            briefs.add(new CreateOrderVO.OrderBrief(orderNo, allocated.shopId,
                    allocated.shopName, allocated.payAmount));
        }

        // 扣减库存（按商品聚合）
        StockChangeRequest stockRequest = new StockChangeRequest();
        stockRequest.setItems(prepared.stockItems);
        Results.data(productInternalApi.deductStock(stockRequest));

        // 核销优惠券（仅单店铺场景），失败回滚库存
        if (prepared.discount.getAppliedCouponId() != null) {
            try {
                CouponUseRequest useRequest = new CouponUseRequest();
                useRequest.setUserId(userId);
                useRequest.setOrderNo(briefs.get(0).getOrderNo());
                Results.data(discountInternalApi.useCoupon(prepared.discount.getAppliedCouponId(), useRequest));
            } catch (RuntimeException ex) {
                log.warn("核销优惠券失败，回滚库存", ex);
                try {
                    Results.data(productInternalApi.restoreStock(stockRequest));
                } catch (Exception restoreEx) {
                    log.error("回滚库存失败，需要人工处理", restoreEx);
                }
                throw ex;
            }
        }

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrders(briefs);
        vo.setOrderNo(briefs.get(0).getOrderNo());
        vo.setPayAmount(briefs.stream().map(CreateOrderVO.OrderBrief::getPayAmount)
                .reduce(ZERO, BigDecimal::add));
        return vo;
    }

    // ==================== 查询 ====================

    public PageResult<OrderVO> page(Long userId, String status, int page, int size) {
        return assemblePage(orderRepository.pageByUser(userId, status, page, size));
    }

    public PageResult<OrderVO> adminPage(String status, int page, int size) {
        return assemblePage(orderRepository.adminPage(status, page, size));
    }

    /** 商家端：本店铺的订单 */
    public PageResult<OrderVO> merchantPage(Long shopId, String status, int page, int size) {
        return assemblePage(orderRepository.pageByShop(shopId, status, page, size));
    }

    public OrderVO detail(Long userId, String orderNo) {
        return toVO(requireOwned(userId, orderNo));
    }

    // ==================== 状态流转 ====================

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

    /** 商家发货：只能发本店铺订单 */
    public void ship(Long shopId, String orderNo) {
        Order order = requireOrder(orderNo);
        if (shopId != null && !shopId.equals(order.getShopId())) {
            throw new BusinessException(OrderErrorCode.ORDER_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        order.ship(now);
        if (!orderRepository.markShipped(orderNo, now)) {
            throw new BusinessException(OrderErrorCode.ORDER_STATUS_INVALID, "只有待发货订单可以发货");
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

    // ==================== 内部逻辑 ====================

    private PreparedOrder prepare(Long userId, List<OrderItemRequest> itemRequests, Long couponId) {
        Map<String, OrderItemRequest> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : itemRequests) {
            Long skuId = item.getSkuId() == null ? 0L : item.getSkuId();
            String key = item.getProductId() + ":" + skuId;
            merged.merge(key, item, (a, b) -> {
                a.setQuantity(a.getQuantity() + b.getQuantity());
                return a;
            });
        }

        List<Long> productIds = merged.values().stream().map(OrderItemRequest::getProductId)
                .distinct().toList();
        ProductBatchRequest batchRequest = new ProductBatchRequest();
        batchRequest.setIds(new ArrayList<>(productIds));
        List<ProductVO> products = Results.data(productInternalApi.batch(batchRequest));
        Map<Long, ProductVO> productMap = products == null ? Collections.emptyMap()
                : products.stream().collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        List<PreparedItem> allItems = new ArrayList<>();
        List<DiscountItem> discountItems = new ArrayList<>();
        List<StockChangeRequest.StockItem> stockItems = new ArrayList<>();
        BigDecimal originalAmount = ZERO;

        for (OrderItemRequest itemRequest : merged.values()) {
            ProductVO product = productMap.get(itemRequest.getProductId());
            if (product == null) {
                throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND,
                        "商品 " + itemRequest.getProductId() + " 不存在");
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BusinessException(OrderErrorCode.PRODUCT_OFF_SHELF, product.getName() + " 已下架");
            }
            Long skuId = itemRequest.getSkuId() == null ? 0L : itemRequest.getSkuId();
            ProductVO.SkuVO sku = findSku(product, skuId);

            int quantity = itemRequest.getQuantity();
            int stock = sku != null && sku.getStock() != null ? sku.getStock()
                    : (product.getStock() == null ? 0 : product.getStock());
            if (stock < quantity) {
                throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, product.getName() + " 库存不足");
            }

            BigDecimal price = sku != null ? sku.getPrice() : product.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            originalAmount = originalAmount.add(subtotal);
            allItems.add(new PreparedItem(product, sku, skuId, quantity, subtotal));

            DiscountItem discountItem = new DiscountItem();
            discountItem.setProductId(product.getId());
            discountItem.setCategoryId(product.getCategoryId());
            discountItem.setPrice(price);
            discountItem.setQuantity(quantity);
            discountItems.add(discountItem);

            StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
            stockItem.setProductId(product.getId());
            stockItem.setQuantity(quantity);
            stockItems.add(stockItem);
        }

        DiscountCalculateRequest discountRequest = new DiscountCalculateRequest();
        discountRequest.setUserId(userId);
        discountRequest.setCouponId(couponId);
        discountRequest.setItems(discountItems);
        DiscountResultVO discount = Results.data(discountInternalApi.calculate(discountRequest));

        Map<Long, List<PreparedItem>> byShop = new LinkedHashMap<>();
        for (PreparedItem item : allItems) {
            byShop.computeIfAbsent(item.product.getShopId(), k -> new ArrayList<>()).add(item);
        }
        List<PreparedShop> shops = byShop.entrySet().stream().map(entry -> {
            BigDecimal shopOriginal = entry.getValue().stream()
                    .map(PreparedItem::subtotal)
                    .reduce(ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            String shopName = entry.getValue().get(0).product.getShopName();
            return new PreparedShop(entry.getKey(), shopName, entry.getValue(), shopOriginal);
        }).toList();

        return new PreparedOrder(allItems, shops, originalAmount, discount, stockItems);
    }

    /** 按店铺小计比例分摊优惠，尾差给最后一个店铺 */
    private List<AllocatedShop> allocateShops(PreparedOrder prepared) {
        List<PreparedShop> shops = prepared.shops;
        BigDecimal totalOriginal = prepared.originalAmount;
        BigDecimal totalPay = prepared.discount.getPayAmount();
        BigDecimal totalPromotion = prepared.discount.getPromotionDiscount() == null
                ? ZERO : prepared.discount.getPromotionDiscount();

        List<AllocatedShop> result = new ArrayList<>();
        BigDecimal allocatedPay = ZERO;
        BigDecimal allocatedPromotion = ZERO;

        for (int i = 0; i < shops.size(); i++) {
            PreparedShop shop = shops.get(i);
            boolean last = i == shops.size() - 1;

            BigDecimal pay;
            BigDecimal promotion;
            if (last) {
                pay = totalPay.subtract(allocatedPay).setScale(2, RoundingMode.HALF_UP);
                promotion = totalPromotion.subtract(allocatedPromotion).setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal ratio = totalOriginal.signum() == 0 ? BigDecimal.ZERO
                        : shop.originalAmount.divide(totalOriginal, 6, RoundingMode.HALF_UP);
                pay = totalPay.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                promotion = totalPromotion.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                allocatedPay = allocatedPay.add(pay);
                allocatedPromotion = allocatedPromotion.add(promotion);
            }
            BigDecimal coupon = shop.originalAmount.subtract(pay).subtract(promotion);
            if (coupon.signum() < 0) {
                coupon = ZERO;
            }
            result.add(new AllocatedShop(shop.shopId, shop.shopName, shop.originalAmount,
                    promotion, coupon, pay, shop.items));
        }
        return result;
    }

    private List<OrderPreviewVO.ShopPreviewVO> buildShopPreviews(PreparedOrder prepared) {
        return allocateShops(prepared).stream().map(allocated -> {
            OrderPreviewVO.ShopPreviewVO vo = new OrderPreviewVO.ShopPreviewVO();
            vo.setShopId(allocated.shopId);
            vo.setShopName(allocated.shopName);
            vo.setOriginalAmount(allocated.originalAmount);
            vo.setPromotionDiscount(allocated.promotionDiscount);
            vo.setCouponDiscount(allocated.couponDiscount);
            vo.setFreightAmount(FREIGHT_AMOUNT);
            vo.setPayAmount(allocated.payAmount);
            vo.setItems(allocated.items.stream().map(this::toPreviewItemVO).toList());
            return vo;
        }).toList();
    }

    private PageResult<OrderVO> assemblePage(PageResult<Order> result) {
        Map<String, List<OrderItem>> itemMap = orderRepository.findItemsByOrderNos(
                result.getList().stream().map(Order::getOrderNo).toList());
        List<OrderVO> records = result.getList().stream()
                .map(order -> toVO(order, itemMap.getOrDefault(order.getOrderNo(), List.of())))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
    }

    private OrderVO toVO(Order order) {
        return toVO(order, orderRepository.findItems(order.getOrderNo()));
    }

    private OrderVO toVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setShopId(order.getShopId());
        vo.setShopName(order.getShopName());
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

    private ProductVO.SkuVO findSku(ProductVO product, Long skuId) {
        if (skuId == null || skuId == 0) {
            return null;
        }
        ProductVO.SkuVO sku = product.getSkus() == null ? null : product.getSkus().stream()
                .filter(s -> skuId.equals(s.getId()))
                .findFirst()
                .orElse(null);
        if (sku == null) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND, "商品规格不存在");
        }
        return sku;
    }

    private StockChangeRequest toStockRequest(List<OrderItem> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItem item : items) {
            merged.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        StockChangeRequest request = new StockChangeRequest();
        request.setItems(merged.entrySet().stream().map(entry -> {
            StockChangeRequest.StockItem stockItem = new StockChangeRequest.StockItem();
            stockItem.setProductId(entry.getKey());
            stockItem.setQuantity(entry.getValue());
            return stockItem;
        }).toList());
        return request;
    }

    private OrderPreviewItemVO toPreviewItemVO(PreparedItem item) {
        OrderPreviewItemVO vo = new OrderPreviewItemVO();
        vo.setProductId(item.product.getId());
        vo.setSkuId(item.skuId);
        vo.setSkuSpecs(item.sku == null ? "" : specsText(item.sku.getSpecs()));
        vo.setProductName(item.product.getName());
        vo.setProductEmoji(item.product.getEmoji());
        vo.setProductColor(item.product.getColor());
        vo.setShopId(item.product.getShopId());
        vo.setShopName(item.product.getShopName());
        vo.setPrice(item.sku != null ? item.sku.getPrice() : item.product.getPrice());
        vo.setQuantity(item.quantity);
        vo.setSubtotal(item.subtotal);
        return vo;
    }

    private OrderItem toOrderItem(Order order, PreparedItem item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(order.getId());
        orderItem.setOrderNo(order.getOrderNo());
        orderItem.setProductId(item.product.getId());
        orderItem.setSkuId(item.skuId);
        orderItem.setSkuSpecs(item.sku == null ? "" : specsText(item.sku.getSpecs()));
        orderItem.setProductName(item.product.getName());
        orderItem.setProductEmoji(item.product.getEmoji());
        orderItem.setProductColor(item.product.getColor());
        orderItem.setShopId(item.product.getShopId());
        orderItem.setShopName(item.product.getShopName());
        orderItem.setPrice(item.sku != null ? item.sku.getPrice() : item.product.getPrice());
        orderItem.setQuantity(item.quantity);
        orderItem.setSubtotal(item.subtotal);
        return orderItem;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setSkuId(item.getSkuId());
        vo.setSkuSpecs(item.getSkuSpecs());
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

    private String specsText(Map<String, String> specs) {
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        return specs.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(" / "));
    }

    private record PreparedItem(ProductVO product, ProductVO.SkuVO sku, Long skuId,
                                int quantity, BigDecimal subtotal) {
    }

    private record PreparedShop(Long shopId, String shopName, List<PreparedItem> items,
                                BigDecimal originalAmount) {
    }

    private record PreparedOrder(List<PreparedItem> allItems,
                                 List<PreparedShop> shops,
                                 BigDecimal originalAmount,
                                 DiscountResultVO discount,
                                 List<StockChangeRequest.StockItem> stockItems) {
    }

    private record AllocatedShop(Long shopId, String shopName, BigDecimal originalAmount,
                                 BigDecimal promotionDiscount, BigDecimal couponDiscount,
                                 BigDecimal payAmount, List<PreparedItem> items) {
    }
}
