package com.four_person.review.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.PageResult;
import com.four_person.common.api.Results;
import com.four_person.common.exception.BusinessException;
import com.four_person.review.api.dto.ReviewDtos;
import com.four_person.review.domain.model.Review;
import com.four_person.review.enums.ReviewErrorCode;
import com.four_person.review.infrastructure.persistence.mapper.ReviewMapper;
import com.four_person.system.api.MemberInternalApi;
import com.four_person.system.api.dto.MemberDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价核心逻辑。
 * <p>
 * 防刷规则：
 * <ul>
 *     <li>仅本人已完成订单中的商品可评，且订单完成不超过 30 天</li>
 *     <li>每单每商品一条，同一用户对同一商品终身只能评价一次</li>
 *     <li>评价内容至少 5 字，命中平台运营配置中的敏感词直接拒绝</li>
 *     <li>单账号每天最多 10 条评价</li>
 *     <li>注册不满 7 天或累计消费不足 100 元的账号，评价先进入平台审核</li>
 *     <li>商家不能购买自己的商品（在订单与购物车服务中拦截）</li>
 * </ul>
 * 商家可举报本店评价，平台业务员审核后决定通过或隐藏；隐藏的评价不计入评分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewApplicationService {

    private static final int MIN_CONTENT_LENGTH = 5;
    private static final int DAILY_LIMIT = 10;
    private static final int REVIEW_WINDOW_DAYS = 30;
    private static final int NEW_ACCOUNT_DAYS = 7;
    private static final BigDecimal MIN_TRUST_SPEND = new BigDecimal("100");

    private final ReviewMapper reviewMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemberInternalApi memberInternalApi;

    /** 提交评价 */
    public ReviewDtos.ReviewVO create(Long userId, ReviewDtos.CreateRequest request) {
        String content = request.getContent().trim();
        if (content.length() < MIN_CONTENT_LENGTH) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CONTENT_TOO_SHORT);
        }
        checkSensitiveWords(content);

        Map<String, Object> orderItem = requireCompletedPurchase(userId, request.getOrderNo(),
                request.getProductId());

        // 已删除的评价仍占用 uk_user_order_product 唯一键，且业务上「同一商品终身只能评价一次」，
        // 因此查重必须连同逻辑删除的记录一起统计，否则会落到数据库唯一键报错
        if (countReviewsIncludingDeleted(userId, request.getProductId()) > 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_DELETED_NO_REVIEW);
        }

        Long sameOrder = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getUserId, userId)
                .eq(Review::getOrderNo, request.getOrderNo())
                .eq(Review::getProductId, request.getProductId()));
        if (sameOrder != null && sameOrder > 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_DONE);
        }
        Long sameProduct = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getUserId, userId)
                .eq(Review::getProductId, request.getProductId()));
        if (sameProduct != null && sameProduct > 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_PRODUCT_DONE);
        }

        Long today = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getUserId, userId)
                .ge(Review::getCreatedAt, LocalDate.now().atStartOfDay()));
        if (today != null && today >= DAILY_LIMIT) {
            throw new BusinessException(ReviewErrorCode.REVIEW_DAILY_LIMIT);
        }

        String nickname = (String) orderItem.get("nickname");
        boolean lowTrust = isLowTrustAccount(userId);

        Review review = new Review();
        review.setProductId(request.getProductId());
        review.setProductName((String) orderItem.get("product_name"));
        review.setShopId(((Number) orderItem.get("shop_id")).longValue());
        review.setOrderNo(request.getOrderNo());
        review.setUserId(userId);
        review.setNickname(nickname);
        review.setRating(request.getRating());
        // 三维度评分：未传时与综合分一致（兼容旧客户端/存量逻辑）
        BigDecimal fallback = BigDecimal.valueOf(request.getRating());
        review.setQualityRating(request.getQualityRating() != null ? request.getQualityRating() : fallback);
        review.setServiceRating(request.getServiceRating() != null ? request.getServiceRating() : fallback);
        review.setLogisticsRating(request.getLogisticsRating() != null ? request.getLogisticsRating() : fallback);
        review.setContent(content);
        review.setImages(writeImages(request.getImages()));
        review.setStatus(lowTrust ? Review.STATUS_PENDING : Review.STATUS_VISIBLE);
        review.setReported(0);
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        if (Review.STATUS_VISIBLE.equals(review.getStatus())) {
            recomputeRatings(review.getProductId(), review.getShopId());
            rewardPoints(review);
        }
        return toVO(review);
    }

    /**
     * 买家删除自己的评价。
     * <p>
     * 软删除（deleted=1）：评价从商品/店铺列表与评分统计中移除；
     * 已发放的评价奖励积分按 bizNo=REVIEW-{id} 扣回，扣回失败则不删除，
     * 保证「积分与评价」始终一致，失败重试也安全（扣回按 bizNo 幂等）。
     * 删除后该商品不可再评价——记录仍占用 uk_user_order_product 唯一键。
     */
    public void delete(Long userId, Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        if (!userId.equals(review.getUserId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN);
        }
        revokeRewardPoints(review);
        reviewMapper.deleteById(reviewId);
        recomputeRatings(review.getProductId(), review.getShopId());
    }

    /** 本人已完成订单中可评价的商品 */
    public List<ReviewDtos.ReviewableVO> reviewable(Long userId, String orderNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT it.product_id, it.product_name, it.sku_specs, it.quantity
                FROM order_info oi
                JOIN order_item it ON it.order_id = oi.id
                WHERE oi.order_no = ? AND oi.user_id = ? AND oi.status = 'COMPLETED' AND oi.deleted = 0
                ORDER BY it.id
                """, orderNo, userId);
        if (rows.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_PURCHASED);
        }
        List<Long> productIds = rows.stream()
                .map(row -> ((Number) row.get("product_id")).longValue())
                .toList();
        // 含已删除的评价：删除后同样不可再评价，列表里应显示为「已评价」
        Set<Long> reviewed = reviewedProductIds(userId, productIds);

        Map<Long, ReviewDtos.ReviewableVO> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long productId = ((Number) row.get("product_id")).longValue();
            if (result.containsKey(productId)) {
                continue;
            }
            ReviewDtos.ReviewableVO vo = new ReviewDtos.ReviewableVO();
            vo.setProductId(productId);
            vo.setProductName((String) row.get("product_name"));
            vo.setSkuSpecs((String) row.get("sku_specs"));
            vo.setQuantity(((Number) row.get("quantity")).intValue());
            vo.setReviewed(reviewed.contains(productId));
            result.put(productId, vo);
        }
        return new ArrayList<>(result.values());
    }

    /** 商品评价列表（仅展示审核通过的评价） */
    public PageResult<ReviewDtos.ReviewVO> pageByProduct(Long productId, int page, int size) {
        Page<Review> p = new Page<>(page, size);
        reviewMapper.selectPage(p, Wrappers.<Review>lambdaQuery()
                .eq(Review::getProductId, productId)
                .eq(Review::getStatus, Review.STATUS_VISIBLE)
                .orderByDesc(Review::getCreatedAt));
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(), page, size);
    }

    /** 商品评分汇总：综合均分 + 三维度均分 + 1-5 星分布 */
    public ReviewDtos.SummaryVO summary(Long productId) {
        Map<String, Object> agg = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       COALESCE(ROUND(AVG(rating), 1), 0) AS avg_rating,
                       COALESCE(ROUND(AVG(quality_rating), 1), 0) AS avg_quality,
                       COALESCE(ROUND(AVG(service_rating), 1), 0) AS avg_service,
                       COALESCE(ROUND(AVG(logistics_rating), 1), 0) AS avg_logistics
                FROM review
                WHERE product_id = ? AND status = 'VISIBLE' AND deleted = 0
                """, productId);
        ReviewDtos.SummaryVO vo = new ReviewDtos.SummaryVO();
        vo.setTotal(((Number) agg.get("total")).longValue());
        vo.setAvgRating((BigDecimal) agg.get("avg_rating"));
        vo.setAvgQuality((BigDecimal) agg.get("avg_quality"));
        vo.setAvgService((BigDecimal) agg.get("avg_service"));
        vo.setAvgLogistics((BigDecimal) agg.get("avg_logistics"));

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            distribution.put(i, 0L);
        }
        jdbcTemplate.queryForList("""
                        SELECT rating, COUNT(*) AS cnt
                        FROM review
                        WHERE product_id = ? AND status = 'VISIBLE' AND deleted = 0
                        GROUP BY rating
                        """, productId)
                .forEach(row -> distribution.put(((Number) row.get("rating")).intValue(),
                        ((Number) row.get("cnt")).longValue()));
        vo.setDistribution(distribution);
        return vo;
    }

    /** 我的评价 */
    public PageResult<ReviewDtos.ReviewVO> mine(Long userId, int page, int size) {
        Page<Review> p = new Page<>(page, size);
        reviewMapper.selectPage(p, Wrappers.<Review>lambdaQuery()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreatedAt));
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(), page, size);
    }

    /** 商家端：本店全部评价（含待审核、已隐藏） */
    public PageResult<ReviewDtos.ReviewVO> pageByShop(Long shopId, int page, int size) {
        Page<Review> p = new Page<>(page, size);
        reviewMapper.selectPage(p, Wrappers.<Review>lambdaQuery()
                .eq(Review::getShopId, shopId)
                .orderByDesc(Review::getReported)
                .orderByDesc(Review::getCreatedAt));
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(), page, size);
    }

    /** 商家举报违规评价，交由平台业务员审核 */
    public void report(Long shopId, Long userId, Long reviewId, String reason) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        if (!shopId.equals(review.getShopId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN);
        }
        if (!Review.STATUS_VISIBLE.equals(review.getStatus())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_STATUS_INVALID);
        }
        if (review.getReported() != null && review.getReported() == 1) {
            throw new BusinessException(ReviewErrorCode.REVIEW_REPORT_DONE);
        }
        LocalDateTime now = LocalDateTime.now();
        review.setReported(1);
        review.setReportReason(reason);
        review.setReportBy(userId);
        review.setReportedAt(now);
        review.setUpdatedAt(now);
        reviewMapper.updateById(review);
    }

    /** 平台：评价审核列表（待审核 / 被举报 / 已隐藏 / 全部） */
    public PageResult<ReviewDtos.ReviewVO> adminPage(String status, int page, int size) {
        var wrapper = Wrappers.<Review>lambdaQuery();
        String filter = status == null ? "" : status.toUpperCase(Locale.ROOT);
        switch (filter) {
            case "PENDING" -> wrapper.eq(Review::getStatus, Review.STATUS_PENDING);
            case "REPORTED" -> wrapper.eq(Review::getReported, 1)
                    .eq(Review::getStatus, Review.STATUS_VISIBLE);
            case "HIDDEN" -> wrapper.eq(Review::getStatus, Review.STATUS_HIDDEN);
            case "VISIBLE" -> wrapper.eq(Review::getStatus, Review.STATUS_VISIBLE);
            default -> { }
        }
        wrapper.orderByDesc(Review::getReported).orderByDesc(Review::getCreatedAt);
        Page<Review> p = new Page<>(page, size);
        reviewMapper.selectPage(p, wrapper);
        return PageResult.of(p.getRecords().stream().map(this::toVO).toList(),
                p.getTotal(), page, size);
    }

    /** 平台：评价审核统计 */
    public ReviewDtos.AdminStatsVO adminStats() {
        ReviewDtos.AdminStatsVO vo = new ReviewDtos.AdminStatsVO();
        vo.setPending(count(Wrappers.<Review>lambdaQuery()
                .eq(Review::getStatus, Review.STATUS_PENDING)));
        vo.setReported(count(Wrappers.<Review>lambdaQuery()
                .eq(Review::getReported, 1)
                .eq(Review::getStatus, Review.STATUS_VISIBLE)));
        vo.setVisible(count(Wrappers.<Review>lambdaQuery()
                .eq(Review::getStatus, Review.STATUS_VISIBLE)));
        vo.setHidden(count(Wrappers.<Review>lambdaQuery()
                .eq(Review::getStatus, Review.STATUS_HIDDEN)));
        return vo;
    }

    /** 平台审核：APPROVE 通过展示 / HIDE 隐藏（软删除，不计入评分） */
    public void moderate(Long userId, Long reviewId, String action, String reason) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(act)) {
            review.setStatus(Review.STATUS_VISIBLE);
        } else if ("HIDE".equals(act)) {
            review.setStatus(Review.STATUS_HIDDEN);
        } else {
            throw new BusinessException(ReviewErrorCode.REVIEW_STATUS_INVALID);
        }
        review.setReported(0);
        review.setAuditReason(reason);
        review.setAuditedBy(userId);
        review.setAuditedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        recomputeRatings(review.getProductId(), review.getShopId());
        // 审核通过后展示（含恢复展示），首次转 VISIBLE 时发放奖励；earnFlat 按 bizNo 幂等，重复通过不重复发放
        if (Review.STATUS_VISIBLE.equals(review.getStatus())) {
            rewardPoints(review);
        }
    }

    /** 商家回复本店展示中的评价，可随时修改回复内容 */
    public void reply(Long shopId, Long reviewId, String replyContent) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        if (!shopId.equals(review.getShopId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_FORBIDDEN);
        }
        if (!Review.STATUS_VISIBLE.equals(review.getStatus())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        review.setReply(replyContent);
        review.setReplyAt(now);
        review.setUpdatedAt(now);
        reviewMapper.updateById(review);
    }

    /** 校验订单归属、完成状态与 30 天时效 */
    private Map<String, Object> requireCompletedPurchase(Long userId, String orderNo, Long productId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT oi.status, oi.complete_time, it.product_name, it.shop_id, u.nickname
                FROM order_info oi
                JOIN order_item it ON it.order_id = oi.id
                JOIN sys_user u ON u.id = oi.user_id
                WHERE oi.order_no = ? AND oi.user_id = ? AND it.product_id = ? AND oi.deleted = 0
                LIMIT 1
                """, orderNo, userId, productId);
        if (rows.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_PURCHASED);
        }
        Map<String, Object> row = rows.get(0);
        if (!"COMPLETED".equals(row.get("status"))) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_PURCHASED);
        }
        LocalDateTime completeTime = toLocalDateTime(row.get("complete_time"));
        if (completeTime != null
                && completeTime.isBefore(LocalDateTime.now().minusDays(REVIEW_WINDOW_DAYS))) {
            throw new BusinessException(ReviewErrorCode.REVIEW_WINDOW_EXPIRED);
        }
        return row;
    }

    /** 注册不足 7 天或累计消费不足 100 元，评价需先审后发 */
    private boolean isLowTrustAccount(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT created_at FROM sys_user WHERE id = ? AND deleted = 0", userId);
        if (rows.isEmpty()) {
            return true;
        }
        LocalDateTime createdAt = toLocalDateTime(rows.get(0).get("created_at"));
        if (createdAt != null
                && createdAt.isAfter(LocalDateTime.now().minusDays(NEW_ACCOUNT_DAYS))) {
            return true;
        }
        BigDecimal spend = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(pay_amount), 0)
                FROM order_info
                WHERE user_id = ? AND status = 'COMPLETED' AND deleted = 0
                """, BigDecimal.class, userId);
        return spend == null || spend.compareTo(MIN_TRUST_SPEND) < 0;
    }

    /** 敏感词来自平台运营配置 sensitive-word 组（JSON 数组或逗号分隔） */
    private void checkSensitiveWords(String content) {
        List<String> words = new ArrayList<>();
        List<String> configs = jdbcTemplate.queryForList(
                "SELECT config_value FROM config_item WHERE config_group = 'sensitive-word'", String.class);
        for (String raw : configs) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(raw);
                if (node.isArray()) {
                    node.forEach(item -> words.add(item.asText()));
                    continue;
                }
            } catch (Exception ignored) {
                // 非 JSON 时按逗号分隔处理
            }
            words.addAll(Arrays.stream(raw.split("[,，\\s]+"))
                    .filter(StringUtils::hasText)
                    .toList());
        }
        String lower = content.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (StringUtils.hasText(word) && lower.contains(word.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ReviewErrorCode.REVIEW_SENSITIVE);
            }
        }
    }

    /** 评价奖励积分：配置中心 points-rule/review-reward（缺省 10，配 0 关闭），bizNo=REVIEW-{id} 幂等 */
    private void rewardPoints(Review review) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT config_value FROM config_item WHERE config_group = 'points-rule' AND config_key = 'review-reward'",
                    String.class);
            int points = 10;
            if (!rows.isEmpty() && StringUtils.hasText(rows.get(0))) {
                points = Integer.parseInt(rows.get(0).trim());
            }
            if (points <= 0) {
                return;
            }
            MemberDtos.EarnFlatRequest request = new MemberDtos.EarnFlatRequest();
            request.setUserId(review.getUserId());
            request.setPoints(points);
            request.setRemark("发表评价奖励");
            request.setBizNo("REVIEW-" + review.getId());
            Integer gain = Results.data(memberInternalApi.earnFlat(request));
            if (gain != null && gain > 0) {
                log.info("评价奖励积分已发放: reviewId={}, userId={}, gain={}",
                        review.getId(), review.getUserId(), gain);
            }
        } catch (Exception ex) {
            // 发放失败不影响评价本身；bizNo 幂等，下次通过审核时可补发
            log.warn("评价奖励积分发放失败: reviewId={}, cause={}", review.getId(), ex.getMessage());
        }
    }

    /** 删除评价时扣回奖励积分：bizNo=REVIEW-{id} 幂等，失败则抛出，评价不删除 */
    private void revokeRewardPoints(Review review) {
        try {
            MemberDtos.RevokeEarnRequest request = new MemberDtos.RevokeEarnRequest();
            request.setUserId(review.getUserId());
            request.setBizNo("REVIEW-" + review.getId());
            request.setRemark("删除评价扣回奖励积分");
            Integer deducted = Results.data(memberInternalApi.revokeEarn(request));
            if (deducted != null && deducted > 0) {
                log.info("删除评价扣回奖励积分: reviewId={}, userId={}, deduct={}",
                        review.getId(), review.getUserId(), deducted);
            }
        } catch (Exception ex) {
            log.error("删除评价扣回积分失败: reviewId={}, cause={}", review.getId(), ex.getMessage());
            throw new BusinessException(ReviewErrorCode.REVIEW_REVOKE_FAILED);
        }
    }

    /** 统计含逻辑删除的评价：删除后不可重评，也避免插入时撞唯一键报错 */
    private int countReviewsIncludingDeleted(Long userId, Long productId) {
        Integer cnt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM review WHERE user_id = ? AND product_id = ?
                """, Integer.class, userId, productId);
        return cnt == null ? 0 : cnt;
    }

    /** 已评价过的商品（含已删除的评价）：删除后同样不可再评价 */
    private Set<Long> reviewedProductIds(Long userId, List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = productIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] args = new Object[productIds.size() + 1];
        args[0] = userId;
        for (int i = 0; i < productIds.size(); i++) {
            args[i + 1] = productIds.get(i);
        }
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT product_id FROM review WHERE user_id = ? AND product_id IN (" + placeholders + ")",
                Long.class, args));
    }

    /** 图片 URL 列表存 JSON 字符串：过滤空值、最多 9 张 */
    private String writeImages(List<String> images) {
        if (images == null) {
            return null;
        }
        List<String> cleaned = images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(9)
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception ex) {
            log.warn("评价图片序列化失败: {}", ex.getMessage());
            return null;
        }
    }

    private List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> images = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(item -> images.add(item.asText()));
            }
            return images;
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** 重算商品评分（本商品评价均值）与店铺评分（全店所有商品评价均值） */
    private void recomputeRatings(Long productId, Long shopId) {
        jdbcTemplate.update("""
                UPDATE product
                SET rating = COALESCE((SELECT ROUND(AVG(rating), 1) FROM review
                                       WHERE product_id = ? AND status = 'VISIBLE' AND deleted = 0), 0)
                WHERE id = ?
                """, productId, productId);
        jdbcTemplate.update("""
                UPDATE shop
                SET rating = COALESCE((SELECT ROUND(AVG(rating), 2) FROM review
                                       WHERE shop_id = ? AND status = 'VISIBLE' AND deleted = 0), 0)
                WHERE id = ?
                """, shopId, shopId);
    }

    /** 兼容 JDBC 驱动返回 LocalDateTime 或 Timestamp 的情况 */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private Long count(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Review> wrapper) {
        return reviewMapper.selectCount(wrapper);
    }

    private ReviewDtos.ReviewVO toVO(Review review) {
        ReviewDtos.ReviewVO vo = new ReviewDtos.ReviewVO();
        vo.setId(review.getId());
        vo.setProductId(review.getProductId());
        vo.setProductName(review.getProductName());
        vo.setShopId(review.getShopId());
        vo.setOrderNo(review.getOrderNo());
        vo.setUserId(review.getUserId());
        vo.setNickname(review.getNickname());
        vo.setRating(review.getRating());
        vo.setQualityRating(review.getQualityRating());
        vo.setServiceRating(review.getServiceRating());
        vo.setLogisticsRating(review.getLogisticsRating());
        vo.setContent(review.getContent());
        vo.setImages(parseImages(review.getImages()));
        vo.setStatus(review.getStatus());
        vo.setReported(review.getReported() != null && review.getReported() == 1);
        vo.setReportReason(review.getReportReason());
        vo.setAuditReason(review.getAuditReason());
        vo.setReply(review.getReply());
        vo.setReplyAt(review.getReplyAt());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }
}
