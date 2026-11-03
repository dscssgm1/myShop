package com.four_person.discount.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.discount.domain.model.PromotionEnrollment;
import com.four_person.discount.domain.repository.PromotionEnrollmentRepository;
import com.four_person.discount.infrastructure.persistence.mapper.PromotionEnrollmentMapper;
import com.four_person.discount.infrastructure.persistence.po.PromotionEnrollmentPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PromotionEnrollmentRepositoryImpl implements PromotionEnrollmentRepository {

    private final PromotionEnrollmentMapper enrollmentMapper;

    @Override
    public PromotionEnrollment save(PromotionEnrollment enrollment) {
        PromotionEnrollmentPO po = toPO(enrollment);
        if (po.getId() == null) {
            enrollmentMapper.insert(po);
            return enrollmentWithId(enrollment, po.getId());
        }
        enrollmentMapper.updateById(po);
        return enrollment;
    }

    private PromotionEnrollment enrollmentWithId(PromotionEnrollment enrollment, Long id) {
        return new PromotionEnrollment(id, enrollment.getPromotionId(), enrollment.getShopId(),
                enrollment.getShopName(), enrollment.getProductId(), enrollment.getProductName(),
                enrollment.getStatus(), enrollment.getReason(), enrollment.getAppliedAt(),
                enrollment.getReviewedAt());
    }

    @Override
    public Optional<PromotionEnrollment> findById(Long id) {
        return Optional.ofNullable(enrollmentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<PromotionEnrollment> findByPromotionAndProduct(Long promotionId, Long productId) {
        return Optional.ofNullable(enrollmentMapper.selectOne(Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                .eq(PromotionEnrollmentPO::getPromotionId, promotionId)
                .eq(PromotionEnrollmentPO::getProductId, productId)
                .last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public List<PromotionEnrollment> findByShop(Long shopId) {
        return enrollmentMapper.selectList(Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                        .eq(PromotionEnrollmentPO::getShopId, shopId)
                        .orderByDesc(PromotionEnrollmentPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<PromotionEnrollment> findByPromotion(Long promotionId) {
        return enrollmentMapper.selectList(Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                        .eq(PromotionEnrollmentPO::getPromotionId, promotionId)
                        .orderByDesc(PromotionEnrollmentPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<PromotionEnrollment> findByStatus(String status) {
        return enrollmentMapper.selectList(Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                        .eq(PromotionEnrollmentPO::getStatus, status)
                        .orderByAsc(PromotionEnrollmentPO::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Long> approvedProductIds(Long promotionId) {
        return enrollmentMapper.selectList(Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                        .eq(PromotionEnrollmentPO::getPromotionId, promotionId)
                        .eq(PromotionEnrollmentPO::getStatus, PromotionEnrollment.STATUS_APPROVED)
                        .select(PromotionEnrollmentPO::getProductId))
                .stream().map(PromotionEnrollmentPO::getProductId).toList();
    }

    @Override
    public Map<Long, Long> countByPromotion() {
        return enrollmentMapper.selectList(Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                        .select(PromotionEnrollmentPO::getPromotionId))
                .stream().collect(Collectors.groupingBy(PromotionEnrollmentPO::getPromotionId,
                        Collectors.counting()));
    }

    @Override
    public PageResult<PromotionEnrollment> page(Long promotionId, Long shopId, String status, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);
        Page<PromotionEnrollmentPO> result = enrollmentMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<PromotionEnrollmentPO>lambdaQuery()
                        .eq(promotionId != null, PromotionEnrollmentPO::getPromotionId, promotionId)
                        .eq(shopId != null, PromotionEnrollmentPO::getShopId, shopId)
                        .eq(status != null && !status.isBlank(), PromotionEnrollmentPO::getStatus, status)
                        .orderByDesc(PromotionEnrollmentPO::getId));
        return PageResult.of(result.getRecords().stream().map(this::toDomain).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    private PromotionEnrollment toDomain(PromotionEnrollmentPO po) {
        return new PromotionEnrollment(po.getId(), po.getPromotionId(), po.getShopId(), po.getShopName(),
                po.getProductId(), po.getProductName(), po.getStatus(), po.getReason(),
                po.getAppliedAt(), po.getReviewedAt());
    }

    private PromotionEnrollmentPO toPO(PromotionEnrollment enrollment) {
        PromotionEnrollmentPO po = new PromotionEnrollmentPO();
        po.setId(enrollment.getId());
        po.setPromotionId(enrollment.getPromotionId());
        po.setShopId(enrollment.getShopId());
        po.setShopName(enrollment.getShopName());
        po.setProductId(enrollment.getProductId());
        po.setProductName(enrollment.getProductName());
        po.setStatus(enrollment.getStatus());
        po.setReason(enrollment.getReason());
        po.setAppliedAt(enrollment.getAppliedAt());
        po.setReviewedAt(enrollment.getReviewedAt());
        return po;
    }
}
