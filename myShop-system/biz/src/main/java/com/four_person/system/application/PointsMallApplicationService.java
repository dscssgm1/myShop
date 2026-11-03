package com.four_person.system.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.order.api.OrderInternalApi;
import com.four_person.order.api.dto.OrderInternalDtos;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.system.api.dto.PointsMallDtos;
import com.four_person.system.domain.model.PointsExchange;
import com.four_person.system.domain.model.PointsMallItem;
import com.four_person.system.infrastructure.persistence.mapper.PointsExchangeMapper;
import com.four_person.system.infrastructure.persistence.mapper.PointsMallItemMapper;
import com.four_person.system.infrastructure.support.PlatformConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 积分商城：平台从商品库挑选商品上架，积分价 = 原价 × 配置中心倍数（points-rule/mall-rate）。
 * 兑换先扣积分，再经订单服务生成免支付兑换订单，失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsMallApplicationService {

    private static final String POINTS_GROUP = "points-rule";
    private static final String KEY_MALL_RATE = "mall-rate";

    private final PointsMallItemMapper itemMapper;
    private final PointsExchangeMapper exchangeMapper;
    private final MembershipApplicationService membershipApplicationService;
    private final PlatformConfigReader configReader;
    private final ProductInternalApi productInternalApi;
    private final OrderInternalApi orderInternalApi;

    // ==================== 用户端 ====================

    /** 上架商品列表 */
    public List<PointsMallDtos.ItemVO> items() {
        BigDecimal rate = mallRate();
        return itemMapper.selectList(Wrappers.<PointsMallItem>lambdaQuery()
                        .eq(PointsMallItem::getStatus, 1)
                        .orderByDesc(PointsMallItem::getId))
                .stream().map(item -> toVO(item, rate)).toList();
    }

    /**
     * 兑换：扣积分 + 生成免支付兑换订单。
     * 与发货编排同构——先本地扣积分，再 Feign 建订单，订单失败抛异常整体回滚返还积分。
     */
    @Transactional(rollbackFor = Exception.class)
    public PointsMallDtos.ExchangeVO exchange(Long userId, PointsMallDtos.ExchangeRequest request) {
        if (!StringUtils.hasText(request.getReceiverName())
                || !StringUtils.hasText(request.getReceiverPhone())
                || !StringUtils.hasText(request.getReceiverAddress())) {
            throw new BusinessException(40001, "请完善收货人、联系电话与收货地址");
        }
        PointsMallItem item = itemMapper.selectById(request.getItemId());
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            throw new BusinessException(40408, "积分商品不存在或已下架");
        }
        int pointsPrice = pointsPrice(item.getProductPrice(), mallRate());

        membershipApplicationService.spend(userId, pointsPrice, "积分兑换：" + item.getProductName());

        OrderInternalDtos.RedeemOrderRequest redeem = new OrderInternalDtos.RedeemOrderRequest();
        redeem.setUserId(userId);
        redeem.setProductId(item.getProductId());
        redeem.setReceiverName(request.getReceiverName().trim());
        redeem.setReceiverPhone(request.getReceiverPhone().trim());
        redeem.setReceiverAddress(request.getReceiverAddress().trim());
        redeem.setPointsUsed(pointsPrice);
        String orderNo;
        try {
            orderNo = Results.data(orderInternalApi.redeemOrder(redeem));
        } catch (Exception ex) {
            log.error("积分兑换创建订单失败: userId={}, itemId={}", userId, item.getId(), ex);
            throw new BusinessException(50002, "兑换下单失败，请稍后重试");
        }
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException(50002, "兑换下单失败，请稍后重试");
        }

        PointsExchange record = new PointsExchange();
        record.setUserId(userId);
        record.setItemId(item.getId());
        record.setItemName(item.getProductName());
        record.setItemEmoji(item.getProductEmoji());
        record.setItemCover(item.getProductCoverImage());
        record.setPoints(pointsPrice);
        record.setOrderNo(orderNo);
        exchangeMapper.insert(record);
        return toExchangeVO(record);
    }

    /** 我的兑换记录 */
    public List<PointsMallDtos.ExchangeVO> exchanges(Long userId) {
        return exchangeMapper.selectList(Wrappers.<PointsExchange>lambdaQuery()
                        .eq(PointsExchange::getUserId, userId)
                        .orderByDesc(PointsExchange::getId))
                .stream().map(this::toExchangeVO).toList();
    }

    // ==================== 平台管理端 ====================

    public List<PointsMallDtos.ItemVO> adminList() {
        BigDecimal rate = mallRate();
        return itemMapper.selectList(Wrappers.<PointsMallItem>lambdaQuery()
                        .orderByDesc(PointsMallItem::getId))
                .stream().map(item -> toVO(item, rate)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PointsMallDtos.ItemVO create(PointsMallDtos.ItemRequest request) {
        Long exists = itemMapper.selectCount(Wrappers.<PointsMallItem>lambdaQuery()
                .eq(PointsMallItem::getProductId, request.getProductId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(40903, "该商品已在积分商城");
        }
        ProductBatchRequest batchRequest = new ProductBatchRequest();
        batchRequest.setIds(new ArrayList<>(List.of(request.getProductId())));
        List<ProductVO> products;
        try {
            products = Results.data(productInternalApi.batch(batchRequest));
        } catch (Exception ex) {
            log.error("积分商城读取商品失败: productId={}", request.getProductId(), ex);
            throw new BusinessException(50003, "商品信息读取失败，请稍后重试");
        }
        ProductVO product = products == null ? null : products.stream()
                .filter(p -> p.getId().equals(request.getProductId())).findFirst().orElse(null);
        if (product == null) {
            throw new BusinessException(40408, "商品不存在");
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(40903, "仅支持上架在售商品");
        }

        PointsMallItem item = new PointsMallItem();
        applyProduct(item, product);
        item.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        itemMapper.insert(item);
        return toVO(item, mallRate());
    }

    @Transactional(rollbackFor = Exception.class)
    public PointsMallDtos.ItemVO updateStatus(Long id, Integer status) {
        PointsMallItem item = requireItem(id);
        item.setStatus(status);
        itemMapper.updateById(item);
        return toVO(item, mallRate());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireItem(id);
        itemMapper.deleteById(id);
    }

    // ==================== 内部逻辑 ====================

    private PointsMallItem requireItem(Long id) {
        PointsMallItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(40408, "积分商品不存在");
        }
        return item;
    }

    private void applyProduct(PointsMallItem item, ProductVO product) {
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductEmoji(product.getEmoji());
        item.setProductColor(product.getColor());
        item.setProductCoverImage(product.getCoverImage());
        item.setProductPrice(product.getPrice());
        item.setShopId(product.getShopId());
        item.setShopName(product.getShopName());
    }

    private BigDecimal mallRate() {
        BigDecimal rate = configReader.getDecimal(POINTS_GROUP, KEY_MALL_RATE, BigDecimal.ONE);
        return rate == null || rate.signum() <= 0 ? BigDecimal.ONE : rate;
    }

    private int pointsPrice(BigDecimal price, BigDecimal rate) {
        if (price == null || price.signum() <= 0) {
            return 0;
        }
        return price.multiply(rate).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private PointsMallDtos.ItemVO toVO(PointsMallItem item, BigDecimal rate) {
        PointsMallDtos.ItemVO vo = new PointsMallDtos.ItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductEmoji(item.getProductEmoji());
        vo.setProductColor(item.getProductColor());
        vo.setProductCoverImage(item.getProductCoverImage());
        vo.setProductPrice(item.getProductPrice());
        vo.setPointsPrice(pointsPrice(item.getProductPrice(), rate));
        vo.setShopId(item.getShopId());
        vo.setShopName(item.getShopName());
        vo.setStatus(item.getStatus());
        return vo;
    }

    private PointsMallDtos.ExchangeVO toExchangeVO(PointsExchange record) {
        PointsMallDtos.ExchangeVO vo = new PointsMallDtos.ExchangeVO();
        vo.setId(record.getId());
        vo.setItemId(record.getItemId());
        vo.setItemName(record.getItemName());
        vo.setItemEmoji(record.getItemEmoji());
        vo.setItemCover(record.getItemCover());
        vo.setPoints(record.getPoints());
        vo.setOrderNo(record.getOrderNo());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
