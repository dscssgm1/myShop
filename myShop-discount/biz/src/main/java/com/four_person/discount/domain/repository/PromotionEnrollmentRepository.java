package com.four_person.discount.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.discount.domain.model.PromotionEnrollment;

import java.util.List;
import java.util.Optional;

public interface PromotionEnrollmentRepository {

    PromotionEnrollment save(PromotionEnrollment enrollment);

    Optional<PromotionEnrollment> findById(Long id);

    /** 同一活动同一商品只允许一条报名记录（重复报名返回 empty，不覆盖） */
    Optional<PromotionEnrollment> findByPromotionAndProduct(Long promotionId, Long productId);

    List<PromotionEnrollment> findByShop(Long shopId);

    List<PromotionEnrollment> findByPromotion(Long promotionId);

    List<PromotionEnrollment> findByStatus(String status);

    /** 活动审核通过的商品 id（算价时圈定活动的实际生效商品） */
    List<Long> approvedProductIds(Long promotionId);

    /** 各活动的报名数量统计，key 为活动 id */
    java.util.Map<Long, Long> countByPromotion();

    PageResult<PromotionEnrollment> page(Long promotionId, Long shopId, String status, int page, int size);
}
