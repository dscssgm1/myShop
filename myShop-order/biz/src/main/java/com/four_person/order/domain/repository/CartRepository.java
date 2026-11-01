package com.four_person.order.domain.repository;

import com.four_person.order.domain.model.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartRepository {

    List<CartItem> findByUser(Long userId);

    Optional<CartItem> findByUserAndProduct(Long userId, Long productId);

    Optional<CartItem> findByIdAndUser(Long id, Long userId);

    CartItem save(CartItem item);

    void deleteById(Long id);

    void clear(Long userId);
}
