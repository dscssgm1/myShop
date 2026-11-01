package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.UserBankCard;

import java.util.List;
import java.util.Optional;

public interface BankCardRepository {

    List<UserBankCard> findByUserId(Long userId);

    Optional<UserBankCard> findById(Long id, Long userId);

    UserBankCard save(UserBankCard card);

    void deleteById(Long id);
}
