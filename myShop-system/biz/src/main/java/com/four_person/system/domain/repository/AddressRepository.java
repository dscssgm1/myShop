package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.UserAddress;

import java.util.List;
import java.util.Optional;

public interface AddressRepository {

    List<UserAddress> findByUserId(Long userId);

    Optional<UserAddress> findById(Long id, Long userId);

    UserAddress save(UserAddress address);

    void deleteById(Long id);

    void clearDefault(Long userId);
}
