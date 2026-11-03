package com.four_person.system.domain.repository;

import com.four_person.system.domain.model.UserTrustedDevice;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository {

    Optional<UserTrustedDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    List<UserTrustedDevice> findByUserId(Long userId);

    UserTrustedDevice save(UserTrustedDevice device);

    void deleteById(Long id, Long userId);
}
