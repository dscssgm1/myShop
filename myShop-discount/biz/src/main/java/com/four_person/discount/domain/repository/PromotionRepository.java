package com.four_person.discount.domain.repository;

import com.four_person.discount.domain.model.Promotion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository {

    List<Promotion> findActive(LocalDateTime now);

    List<Promotion> findAll();

    List<Promotion> findByIds(List<Long> ids);

    /** 指定店铺的全部活动（含已结束），商家自建活动列表用 */
    List<Promotion> findByShop(Long shopId);

    Optional<Promotion> findById(Long id);

    Promotion save(Promotion promotion);
}
