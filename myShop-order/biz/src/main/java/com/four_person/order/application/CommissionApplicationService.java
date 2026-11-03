package com.four_person.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.common.api.Results;
import com.four_person.order.api.dto.CommissionDtos;
import com.four_person.order.domain.model.CommissionRecord;
import com.four_person.order.domain.model.Order;
import com.four_person.order.domain.model.OrderItem;
import com.four_person.order.domain.repository.OrderRepository;
import com.four_person.order.infrastructure.persistence.mapper.CommissionRecordMapper;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.CommissionRateRequest;
import com.four_person.product.api.dto.CommissionRateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台对商品成交款的抽佣：
 * 订单完成（手动确认收货 / 签收满 7 天自动完成）时逐商品行按其类目费率计佣，
 * 汇总写一条 COMMISSION 流水；售后退款成功时写一条金额为负的 REVERSAL 流水冲销。
 * (order_no, type) 唯一键保证同一订单只计佣 / 只冲销一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionApplicationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String YUAN = "¥";

    private final CommissionRecordMapper commissionRecordMapper;
    private final OrderRepository orderRepository;
    private final ProductInternalApi productInternalApi;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 订单完成计佣。独立事务（REQUIRES_NEW）：计佣失败只记日志，不影响确认收货这一业务事实。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(Order order) {
        // 0 元单（积分兑换订单等）没有成交款可抽佣
        if (order.getPayAmount() == null || order.getPayAmount().signum() <= 0) {
            return;
        }
        List<OrderItem> items = orderRepository.findItems(order.getOrderNo());
        if (items.isEmpty()) {
            return;
        }
        Map<Long, CommissionRateVO> rates = categoryRates(items);
        if (rates.isEmpty()) {
            return;
        }

        List<Line> lines = allocateLines(order, items);
        BigDecimal goodsAmount = BigDecimal.ZERO;
        BigDecimal commissionAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal[]> perCategory = new LinkedHashMap<>();
        for (Line line : lines) {
            CommissionRateVO rateVO = rates.get(line.categoryId);
            BigDecimal rate = rateVO == null ? BigDecimal.ZERO : rateVO.getRate();
            if (rate == null || rate.signum() <= 0) {
                continue; // 该行类目不抽佣
            }
            BigDecimal commission = line.amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            goodsAmount = goodsAmount.add(line.amount);
            commissionAmount = commissionAmount.add(commission);
            BigDecimal[] acc = perCategory.computeIfAbsent(line.categoryId, k -> new BigDecimal[]{BigDecimal.ZERO, rate});
            acc[0] = acc[0].add(line.amount);
        }
        if (commissionAmount.signum() <= 0) {
            return; // 全部类目费率为 0，不留空流水
        }

        CommissionRecord record = new CommissionRecord();
        record.setOrderNo(order.getOrderNo());
        record.setType(CommissionRecord.TYPE_COMMISSION);
        record.setShopId(order.getShopId());
        record.setShopName(order.getShopName());
        record.setGoodsAmount(goodsAmount);
        record.setCommissionRate(goodsAmount.signum() == 0 ? BigDecimal.ZERO
                : commissionAmount.multiply(HUNDRED).divide(goodsAmount, 2, RoundingMode.HALF_UP));
        record.setCommissionAmount(commissionAmount);
        record.setMerchantAmount(goodsAmount.subtract(commissionAmount));
        record.setDetail(describe(perCategory, rates));
        record.setReason("订单完成计佣");
        try {
            commissionRecordMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            // 同订单已计佣（确认收货与自动完成并发），幂等跳过
        }
    }

    /** 售后退款冲销：只冲销已经计过佣的订单，金额取原佣金流水 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverse(String orderNo) {
        CommissionRecord origin = findRecord(orderNo, CommissionRecord.TYPE_COMMISSION);
        if (origin == null) {
            return; // 订单尚未计佣（未完成即退款），无需冲销
        }
        CommissionRecord record = new CommissionRecord();
        record.setOrderNo(orderNo);
        record.setType(CommissionRecord.TYPE_REVERSAL);
        record.setShopId(origin.getShopId());
        record.setShopName(origin.getShopName());
        record.setGoodsAmount(origin.getGoodsAmount().negate());
        record.setCommissionRate(origin.getCommissionRate());
        record.setCommissionAmount(origin.getCommissionAmount().negate());
        record.setMerchantAmount(origin.getMerchantAmount().negate());
        record.setDetail(origin.getDetail());
        record.setReason("售后退款冲销");
        try {
            commissionRecordMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            // 已冲销过，幂等跳过
        }
    }

    /** 商家账单：账户汇总 + 本店铺流水 */
    public CommissionDtos.CommissionSummaryVO summary(Long shopId) {
        CommissionDtos.CommissionSummaryVO vo = new CommissionDtos.CommissionSummaryVO();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(goods_amount), 0) AS goods_sum, "
                        + "COALESCE(SUM(commission_amount), 0) AS commission_sum, "
                        + "COALESCE(SUM(merchant_amount), 0) AS merchant_sum, "
                        + "COALESCE(SUM(CASE WHEN type = 'COMMISSION' THEN 1 ELSE 0 END), 0) AS commission_cnt, "
                        + "COALESCE(SUM(CASE WHEN type = 'REVERSAL' THEN 1 ELSE 0 END), 0) AS reversal_cnt "
                        + "FROM commission_record WHERE shop_id = ?", shopId);
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        vo.setTotalGoodsAmount(toDecimal(row, "goods_sum"));
        vo.setTotalCommissionAmount(toDecimal(row, "commission_sum"));
        vo.setTotalMerchantAmount(toDecimal(row, "merchant_sum"));
        vo.setCommissionCount(toLong(row, "commission_cnt"));
        vo.setReversalCount(toLong(row, "reversal_cnt"));
        return vo;
    }

    /** 平台账单：全平台汇总 + 各店铺累计 */
    public CommissionDtos.CommissionAdminSummaryVO adminSummary() {
        CommissionDtos.CommissionAdminSummaryVO vo = new CommissionDtos.CommissionAdminSummaryVO();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(goods_amount), 0) AS goods_sum, "
                        + "COALESCE(SUM(commission_amount), 0) AS commission_sum, "
                        + "COALESCE(SUM(merchant_amount), 0) AS merchant_sum, "
                        + "COALESCE(SUM(CASE WHEN type = 'COMMISSION' THEN 1 ELSE 0 END), 0) AS commission_cnt, "
                        + "COALESCE(SUM(CASE WHEN type = 'REVERSAL' THEN 1 ELSE 0 END), 0) AS reversal_cnt "
                        + "FROM commission_record");
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        vo.setTotalGoodsAmount(toDecimal(row, "goods_sum"));
        vo.setTotalCommissionAmount(toDecimal(row, "commission_sum"));
        vo.setTotalMerchantAmount(toDecimal(row, "merchant_sum"));
        vo.setCommissionCount(toLong(row, "commission_cnt"));
        vo.setReversalCount(toLong(row, "reversal_cnt"));

        List<Map<String, Object>> shopRows = jdbcTemplate.queryForList(
                "SELECT shop_id, MAX(shop_name) AS shop_name, "
                        + "COALESCE(SUM(goods_amount), 0) AS goods_sum, "
                        + "COALESCE(SUM(commission_amount), 0) AS commission_sum, "
                        + "COALESCE(SUM(merchant_amount), 0) AS merchant_sum, "
                        + "COALESCE(COUNT(id), 0) AS record_cnt "
                        + "FROM commission_record GROUP BY shop_id ORDER BY commission_sum DESC");
        List<CommissionDtos.ShopCommissionVO> shops = new ArrayList<>();
        for (Map<String, Object> shopRow : shopRows) {
            CommissionDtos.ShopCommissionVO sv = new CommissionDtos.ShopCommissionVO();
            sv.setShopId(toLong(shopRow, "shop_id"));
            sv.setShopName(shopRow.get("shop_name") == null ? "" : String.valueOf(shopRow.get("shop_name")));
            sv.setGoodsAmount(toDecimal(shopRow, "goods_sum"));
            sv.setCommissionAmount(toDecimal(shopRow, "commission_sum"));
            sv.setMerchantAmount(toDecimal(shopRow, "merchant_sum"));
            sv.setCount(toLong(shopRow, "record_cnt"));
            shops.add(sv);
        }
        vo.setShops(shops);
        return vo;
    }

    /** 佣金流水分页：shopId 为空时平台看全量 */
    public PageResult<CommissionDtos.CommissionVO> page(Long shopId, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);
        LambdaQueryWrapper<CommissionRecord> wrapper = Wrappers.<CommissionRecord>lambdaQuery()
                .eq(shopId != null, CommissionRecord::getShopId, shopId)
                .orderByDesc(CommissionRecord::getId);
        Page<CommissionRecord> result = commissionRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    // ==================== 计佣计算 ====================

    /** 商品行与计入佣金的实收金额（优惠按行金额占比分摊，余数计入最后一行，与一店一单的拆分口径一致） */
    private record Line(Long categoryId, BigDecimal amount) {
    }

    private List<Line> allocateLines(Order order, List<OrderItem> items) {
        BigDecimal original = order.getOriginalAmount() == null ? BigDecimal.ZERO : order.getOriginalAmount();
        BigDecimal pay = order.getPayAmount();
        List<Line> lines = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            BigDecimal subtotal = item.getSubtotal() == null ? BigDecimal.ZERO : item.getSubtotal();
            BigDecimal amount;
            if (i == items.size() - 1) {
                amount = pay.subtract(allocated); // 最后一行吃掉分摊余数，保证合计等于实付
            } else if (original.signum() > 0) {
                amount = pay.multiply(subtotal).divide(original, 2, RoundingMode.HALF_UP);
            } else {
                amount = BigDecimal.ZERO;
            }
            if (amount.signum() < 0) {
                amount = BigDecimal.ZERO;
            }
            allocated = allocated.add(amount);
            lines.add(new Line(item.getCategoryId(), amount));
        }
        return lines;
    }

    /** 批量取类目生效费率；快照缺失（历史订单）时回查商品表补齐 */
    private Map<Long, CommissionRateVO> categoryRates(List<OrderItem> items) {
        List<Long> categoryIds = items.stream()
                .map(OrderItem::getCategoryId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        List<OrderItem> legacy = items.stream()
                .filter(item -> item.getCategoryId() == null || item.getCategoryId() <= 0)
                .toList();
        if (!legacy.isEmpty()) {
            // 历史订单没存类目快照，按商品回查并回填到内存里的行
            Map<Long, Long> productCategories = productCategories(
                    legacy.stream().map(OrderItem::getProductId).distinct().toList());
            legacy.forEach(item -> item.setCategoryId(productCategories.get(item.getProductId())));
            legacy.stream()
                    .map(OrderItem::getCategoryId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .forEach(categoryIds::add);
        }
        Map<Long, CommissionRateVO> rates = new LinkedHashMap<>();
        if (categoryIds.isEmpty()) {
            return rates;
        }
        try {
            CommissionRateRequest request = new CommissionRateRequest();
            request.setCategoryIds(categoryIds);
            List<CommissionRateVO> result = Results.data(productInternalApi.commissionRates(request));
            if (result != null) {
                result.forEach(rate -> rates.put(rate.getCategoryId(), rate));
            }
        } catch (Exception ex) {
            log.warn("读取类目佣金费率失败，本次不计佣: categoryIds={}", categoryIds, ex);
            return Map.of();
        }
        return rates;
    }

    private Map<Long, Long> productCategories(List<Long> productIds) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, category_id FROM product WHERE id IN ("
                            + productIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")",
                    productIds.toArray());
            Map<Long, Long> map = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                map.put(toLong(row, "id"), toLong(row, "category_id"));
            }
            return map;
        } catch (Exception ex) {
            log.warn("历史订单回查商品类目失败: productIds={}", productIds, ex);
            return Map.of();
        }
    }

    /** 计佣明细，如「手机数码 ¥3999.00 × 5% = ¥199.95」 */
    private String describe(Map<Long, BigDecimal[]> perCategory, Map<Long, CommissionRateVO> rates) {
        return perCategory.entrySet().stream()
                .map(entry -> {
                    BigDecimal amount = entry.getValue()[0];
                    BigDecimal rate = entry.getValue()[1];
                    BigDecimal commission = amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    CommissionRateVO rateVO = rates.get(entry.getKey());
                    String name = rateVO == null || !StringUtils.hasText(rateVO.getCategoryName())
                            ? "类目 " + entry.getKey() : rateVO.getCategoryName();
                    return name + " " + YUAN + amount.toPlainString()
                            + " × " + rate.stripTrailingZeros().toPlainString() + "% = " + YUAN + commission.toPlainString();
                })
                .collect(Collectors.joining("；"));
    }

    private CommissionRecord findRecord(String orderNo, String type) {
        return commissionRecordMapper.selectOne(Wrappers.<CommissionRecord>lambdaQuery()
                .eq(CommissionRecord::getOrderNo, orderNo)
                .eq(CommissionRecord::getType, type)
                .last("LIMIT 1"));
    }

    private CommissionDtos.CommissionVO toVO(CommissionRecord record) {
        CommissionDtos.CommissionVO vo = new CommissionDtos.CommissionVO();
        vo.setId(record.getId());
        vo.setOrderNo(record.getOrderNo());
        vo.setType(record.getType());
        vo.setTypeText(CommissionRecord.TYPE_REVERSAL.equals(record.getType()) ? "售后退款冲销" : "订单完成计佣");
        vo.setShopId(record.getShopId());
        vo.setShopName(record.getShopName());
        vo.setGoodsAmount(record.getGoodsAmount());
        vo.setCommissionRate(record.getCommissionRate());
        vo.setCommissionAmount(record.getCommissionAmount());
        vo.setMerchantAmount(record.getMerchantAmount());
        vo.setDetail(record.getDetail());
        vo.setReason(record.getReason());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private static BigDecimal toDecimal(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private static long toLong(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
