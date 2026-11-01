package com.four_person.order.application;

import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.dto.CartAddRequest;
import com.four_person.order.api.dto.CartItemVO;
import com.four_person.order.api.dto.CartUpdateRequest;
import com.four_person.order.api.dto.CartVO;
import com.four_person.order.domain.model.CartItem;
import com.four_person.order.domain.repository.CartRepository;
import com.four_person.order.enums.OrderErrorCode;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartApplicationService {

    private final CartRepository cartRepository;
    private final ProductInternalApi productInternalApi;

    public CartVO get(Long userId) {
        List<CartItem> items = cartRepository.findByUser(userId);
        Map<Long, ProductVO> products = loadProducts(items);
        List<CartItemVO> itemVOs = items.stream()
                .map(item -> toVO(item, products.get(item.getProductId())))
                .toList();
        CartVO cart = new CartVO();
        cart.setItems(itemVOs);
        cart.setTotalQuantity(itemVOs.stream().mapToInt(CartItemVO::getQuantity).sum());
        cart.setTotalAmount(itemVOs.stream()
                .map(CartItemVO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));
        return cart;
    }

    public CartVO add(Long userId, CartAddRequest request) {
        ProductVO product = requireProduct(request.getProductId());
        CartItem item = cartRepository.findByUserAndProduct(userId, request.getProductId())
                .orElseGet(CartItem::new);
        int quantity = request.getQuantity() + (item.getQuantity() == null ? 0 : item.getQuantity());
        requireStock(product, quantity);
        item.setUserId(userId);
        item.setProductId(product.getId());
        applyProduct(item, product);
        item.setQuantity(quantity);
        cartRepository.save(item);
        return get(userId);
    }

    public CartVO update(Long userId, Long itemId, CartUpdateRequest request) {
        CartItem item = requireCartItem(userId, itemId);
        ProductVO product = requireProduct(item.getProductId());
        requireStock(product, request.getQuantity());
        applyProduct(item, product);
        item.setQuantity(request.getQuantity());
        cartRepository.save(item);
        return get(userId);
    }

    public CartVO remove(Long userId, Long itemId) {
        requireCartItem(userId, itemId);
        cartRepository.deleteById(itemId);
        return get(userId);
    }

    public void clear(Long userId) {
        cartRepository.clear(userId);
    }

    private CartItem requireCartItem(Long userId, Long itemId) {
        return cartRepository.findByIdAndUser(itemId, userId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));
    }

    private ProductVO requireProduct(Long productId) {
        ProductBatchRequest request = new ProductBatchRequest();
        request.setIds(List.of(productId));
        List<ProductVO> products = Results.data(productInternalApi.batch(request));
        ProductVO product = products == null || products.isEmpty() ? null : products.get(0);
        if (product == null) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(OrderErrorCode.PRODUCT_OFF_SHELF);
        }
        return product;
    }

    private void requireStock(ProductVO product, int quantity) {
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, product.getName() + " 库存不足");
        }
    }

    private void applyProduct(CartItem item, ProductVO product) {
        item.setProductName(product.getName());
        item.setPrice(product.getPrice());
        item.setShopId(product.getShopId());
        item.setShopName(product.getShopName());
        item.setEmoji(product.getEmoji());
        item.setColor(product.getColor());
    }

    private Map<Long, ProductVO> loadProducts(List<CartItem> items) {
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            ProductBatchRequest request = new ProductBatchRequest();
            request.setIds(items.stream().map(CartItem::getProductId).distinct().toList());
            List<ProductVO> products = Results.data(productInternalApi.batch(request));
            return products == null ? Collections.emptyMap() : products.stream()
                    .collect(Collectors.toMap(ProductVO::getId, Function.identity()));
        } catch (Exception ex) {
            log.warn("获取购物车商品信息失败，使用本地快照: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private CartItemVO toVO(CartItem item, ProductVO product) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setQuantity(item.getQuantity());
        vo.setShopId(item.getShopId());
        vo.setShopName(item.getShopName());
        vo.setEmoji(item.getEmoji());
        vo.setColor(item.getColor());
        BigDecimal price = product == null ? item.getPrice() : product.getPrice();
        vo.setPrice(price);
        vo.setProductName(product == null ? item.getProductName() : product.getName());
        vo.setStock(product == null ? null : product.getStock());
        vo.setSubtotal(price.multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP));
        return vo;
    }
}
