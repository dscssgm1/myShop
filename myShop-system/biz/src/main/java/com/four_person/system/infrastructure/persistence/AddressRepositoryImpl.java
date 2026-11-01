package com.four_person.system.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.system.domain.model.UserAddress;
import com.four_person.system.domain.repository.AddressRepository;
import com.four_person.system.infrastructure.persistence.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AddressRepositoryImpl implements AddressRepository {

    private final UserAddressMapper mapper;

    @Override
    public List<UserAddress> findByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    @Override
    public Optional<UserAddress> findById(Long id, Long userId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId)));
    }

    @Override
    public UserAddress save(UserAddress address) {
        if (address.getId() == null) {
            mapper.insert(address);
        } else {
            mapper.updateById(address);
        }
        return address;
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void clearDefault(Long userId) {
        mapper.update(null, Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0));
    }
}
