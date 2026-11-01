package com.four_person.system.application;

import com.four_person.common.exception.BusinessException;
import com.four_person.system.api.dto.ProfileDtos;
import com.four_person.system.domain.model.UserAddress;
import com.four_person.system.domain.repository.AddressRepository;
import com.four_person.system.enums.SystemErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressApplicationService {

    private final AddressRepository addressRepository;

    public List<ProfileDtos.AddressVO> list(Long userId) {
        return addressRepository.findByUserId(userId).stream().map(this::toVO).toList();
    }

    @Transactional
    public ProfileDtos.AddressVO create(Long userId, ProfileDtos.AddressRequest request) {
        boolean first = addressRepository.findByUserId(userId).isEmpty();
        boolean asDefault = Boolean.TRUE.equals(request.getIsDefault()) || first;
        if (asDefault) {
            addressRepository.clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setAddress(request.getAddress());
        address.setIsDefault(asDefault ? 1 : 0);
        addressRepository.save(address);
        return toVO(address);
    }

    @Transactional
    public ProfileDtos.AddressVO update(Long userId, Long id, ProfileDtos.AddressRequest request) {
        UserAddress address = addressRepository.findById(id, userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ADDRESS_NOT_FOUND));
        boolean asDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (asDefault) {
            addressRepository.clearDefault(userId);
        }
        address.setReceiverName(request.getReceiverName());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setAddress(request.getAddress());
        address.setIsDefault(asDefault ? 1 : 0);
        addressRepository.save(address);
        return toVO(address);
    }

    @Transactional
    public void setDefault(Long userId, Long id) {
        UserAddress address = addressRepository.findById(id, userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ADDRESS_NOT_FOUND));
        addressRepository.clearDefault(userId);
        address.setIsDefault(1);
        addressRepository.save(address);
    }

    public void delete(Long userId, Long id) {
        addressRepository.findById(id, userId)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.ADDRESS_NOT_FOUND));
        addressRepository.deleteById(id);
    }

    private ProfileDtos.AddressVO toVO(UserAddress address) {
        ProfileDtos.AddressVO vo = new ProfileDtos.AddressVO();
        vo.setId(address.getId());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setAddress(address.getAddress());
        vo.setIsDefault(address.getIsDefault() != null && address.getIsDefault() == 1);
        vo.setCreatedAt(address.getCreatedAt());
        return vo;
    }
}
