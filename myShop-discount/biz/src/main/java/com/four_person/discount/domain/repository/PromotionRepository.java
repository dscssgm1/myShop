package com.four_person.discount.domain.repository;

import com.four_person.discount.domain.model.Promotion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository {

    List<Promotion> findActive(LocalDateTime now);

    List<Promotion> findAll();

    Optional<Promotion> findById(Long id);

    Promotion save(Promotion promotion);
}
