package com.four_person.order.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.order.domain.model.CartItem;
import com.four_person.order.domain.repository.CartRepository;
import com.four_person.order.infrastructure.persistence.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    private final CartItemMapper cartItemMapper;

    @Override
    public List<CartItem> findByUser(Long userId) {
        return cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId));
    }

    @Override
    public Optional<CartItem> findByUserAndProductSku(Long userId, Long productId, Long skuId) {
        return Optional.ofNullable(cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, productId)
                .eq(CartItem::getSkuId, skuId == null ? 0L : skuId)));
    }

    @Override
    public Optional<CartItem> findByIdAndUser(Long id, Long userId) {
        return Optional.ofNullable(cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, userId)));
    }

    @Override
    public CartItem save(CartItem item) {
        if (item.getId() == null) {
            cartItemMapper.insert(item);
        } else {
            cartItemMapper.updateById(item);
        }
        return item;
    }

    @Override
    public void deleteById(Long id) {
        cartItemMapper.deleteById(id);
    }

    @Override
    public void clear(Long userId) {
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));
    }
}
