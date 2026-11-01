package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.system.domain.model.UserBankCard;
import com.four_person.system.domain.repository.BankCardRepository;
import com.four_person.system.infrastructure.persistence.mapper.UserBankCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BankCardRepositoryImpl implements BankCardRepository {

    private final UserBankCardMapper mapper;

    @Override
    public List<UserBankCard> findByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserBankCard>lambdaQuery()
                .eq(UserBankCard::getUserId, userId)
                .orderByDesc(UserBankCard::getId));
    }

    @Override
    public Optional<UserBankCard> findById(Long id, Long userId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<UserBankCard>lambdaQuery()
                .eq(UserBankCard::getId, id)
                .eq(UserBankCard::getUserId, userId)));
    }

    @Override
    public UserBankCard save(UserBankCard card) {
        if (card.getId() == null) {
            mapper.insert(card);
        } else {
            mapper.updateById(card);
        }
        return card;
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }
}
