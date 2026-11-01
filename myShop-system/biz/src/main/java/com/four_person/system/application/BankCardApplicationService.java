package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.ProfileDtos;
import com.four_person.system.domain.model.UserBankCard;
import com.four_person.system.domain.repository.BankCardRepository;
import com.four_person.system.enums.SystemErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankCardApplicationService {

    private final BankCardRepository bankCardRepository;

    public List<ProfileDtos.BankCardVO> list(Long userId) {
        return bankCardRepository.findByUserId(userId).stream().map(this::toVO).toList();
    }

    public ProfileDtos.BankCardVO create(Long userId, ProfileDtos.BankCardRequest request) {
        UserBankCard card = new UserBankCard();
        card.setUserId(userId);
        card.setBankName(request.getBankName());
        card.setCardNo(request.getCardNo());
        card.setCardType(request.getCardType());
        card.setHolder(request.getHolder());
        bankCardRepository.save(card);
        return toVO(card);
    }

    public void delete(Long userId, Long id) {
        bankCardRepository.findById(id, userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.BANK_CARD_NOT_FOUND));
        bankCardRepository.deleteById(id);
    }

    private ProfileDtos.BankCardVO toVO(UserBankCard card) {
        ProfileDtos.BankCardVO vo = new ProfileDtos.BankCardVO();
        vo.setId(card.getId());
        vo.setBankName(card.getBankName());
        vo.setCardNo(mask(card.getCardNo()));
        vo.setCardType(card.getCardType());
        vo.setHolder(card.getHolder());
        vo.setCreatedAt(card.getCreatedAt());
        return vo;
    }

    /** 6225 **** **** 1234 */
    private String mask(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) {
            return cardNo;
        }
        return cardNo.replaceAll("(\\d{4})\\d+(\\d{4})", "$1 **** **** $2");
    }
}
