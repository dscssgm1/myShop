package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.system.domain.model.UserTrustedDevice;
import com.four_person.system.domain.repository.TrustedDeviceRepository;
import com.four_person.system.infrastructure.persistence.mapper.UserTrustedDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TrustedDeviceRepositoryImpl implements TrustedDeviceRepository {

    private final UserTrustedDeviceMapper mapper;

    @Override
    public Optional<UserTrustedDevice> findByUserIdAndDeviceId(Long userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<UserTrustedDevice>lambdaQuery()
                .eq(UserTrustedDevice::getUserId, userId)
                .eq(UserTrustedDevice::getDeviceId, deviceId)));
    }

    @Override
    public List<UserTrustedDevice> findByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserTrustedDevice>lambdaQuery()
                .eq(UserTrustedDevice::getUserId, userId)
                .orderByDesc(UserTrustedDevice::getId));
    }

    @Override
    public UserTrustedDevice save(UserTrustedDevice device) {
        if (device.getId() == null) {
            mapper.insert(device);
        } else {
            mapper.updateById(device);
        }
        return device;
    }

    @Override
    public void deleteById(Long id, Long userId) {
        mapper.delete(Wrappers.<UserTrustedDevice>lambdaQuery()
                .eq(UserTrustedDevice::getId, id)
                .eq(UserTrustedDevice::getUserId, userId));
    }
}
