package com.four_person.discount.domain.repository;

import com.four_person.discount.domain.model.CouponTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {

    List<CouponTemplate> findActive(LocalDateTime now);

    /** 指定店铺发放的全部券（含已结束），商家券管理用 */
    List<CouponTemplate> findByShop(Long shopId);

    Optional<CouponTemplate> findById(Long id);

    List<CouponTemplate> findByIds(List<Long> ids);

    CouponTemplate save(CouponTemplate template);

    /**
     * 原子占用一张券库存，失败表示已领完或模板不可用。
     */
    boolean increaseClaimed(Long id);
}
