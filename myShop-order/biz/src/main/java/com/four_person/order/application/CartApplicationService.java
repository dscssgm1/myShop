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
import java.util.Optional;
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
        Long skuId = request.getSkuId() == null ? 0L : request.getSkuId();
        ProductVO.SkuVO sku = findSku(product, skuId);

        CartItem item = cartRepository.findByUserAndProductSku(userId, request.getProductId(), skuId)
                .orElseGet(CartItem::new);
        int quantity = request.getQuantity() + (item.getQuantity() == null ? 0 : item.getQuantity());
        requireStock(product, sku, quantity);
        item.setUserId(userId);
        item.setProductId(product.getId());
        item.setSkuId(skuId);
        applyProduct(item, product, sku);
        item.setQuantity(quantity);
        cartRepository.save(item);
        return get(userId);
    }

    public CartVO update(Long userId, Long itemId, CartUpdateRequest request) {
        CartItem item = requireCartItem(userId, itemId);
        ProductVO product = requireProduct(item.getProductId());
        ProductVO.SkuVO sku = findSku(product, item.getSkuId());
        requireStock(product, sku, request.getQuantity());
        applyProduct(item, product, sku);
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

    private void requireStock(ProductVO product, ProductVO.SkuVO sku, int quantity) {
        int stock = sku != null && sku.getStock() != null ? sku.getStock()
                : (product.getStock() == null ? 0 : product.getStock());
        if (stock < quantity) {
            throw new BusinessException(OrderErrorCode.STOCK_INSUFFICIENT, product.getName() + " 库存不足");
        }
    }

    private void applyProduct(CartItem item, ProductVO product, ProductVO.SkuVO sku) {
        item.setProductName(product.getName());
        item.setPrice(sku != null ? sku.getPrice() : product.getPrice());
        item.setSkuSpecs(sku == null ? "" : specsText(sku.getSpecs()));
        item.setShopId(product.getShopId());
        item.setShopName(product.getShopName());
        item.setEmoji(product.getEmoji());
        item.setColor(product.getColor());
    }

    private String specsText(Map<String, String> specs) {
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        return specs.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(" / "));
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
        vo.setSkuId(item.getSkuId());
        vo.setSkuSpecs(item.getSkuSpecs());
        vo.setQuantity(item.getQuantity());
        vo.setShopId(item.getShopId());
        vo.setShopName(item.getShopName());
        vo.setEmoji(item.getEmoji());
        vo.setColor(item.getColor());

        BigDecimal price = item.getPrice();
        Integer stock = null;
        if (product != null) {
            ProductVO.SkuVO sku = Optional.ofNullable(product.getSkus()).orElse(List.of()).stream()
                    .filter(s -> item.getSkuId() != null && item.getSkuId().equals(s.getId()))
                    .findFirst().orElse(null);
            price = sku != null ? sku.getPrice() : product.getPrice();
            stock = sku != null && sku.getStock() != null ? sku.getStock() : product.getStock();
            vo.setProductName(product.getName());
        } else {
            vo.setProductName(item.getProductName());
        }
        vo.setPrice(price);
        vo.setStock(stock);
        vo.setSubtotal(price.multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP));
        return vo;
    }
}
