package com.four_person.order.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.order.domain.model.OrderGroup;
import com.four_person.order.domain.repository.OrderGroupRepository;
import com.four_person.order.infrastructure.persistence.mapper.OrderGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderGroupRepositoryImpl implements OrderGroupRepository {

    private final OrderGroupMapper orderGroupMapper;

    @Override
    public OrderGroup save(OrderGroup group) {
        if (group.getId() == null) {
            orderGroupMapper.insert(group);
        } else {
            orderGroupMapper.updateById(group);
        }
        return group;
    }

    @Override
    public Optional<OrderGroup> findByGroupNo(String groupNo) {
        return Optional.ofNullable(orderGroupMapper.selectOne(Wrappers.<OrderGroup>lambdaQuery()
                .eq(OrderGroup::getGroupNo, groupNo)));
    }

    @Override
    public boolean markPaid(String groupNo, LocalDateTime time) {
        return orderGroupMapper.markPaid(groupNo, time) > 0;
    }

    @Override
    public boolean markCancelled(String groupNo, LocalDateTime time) {
        return orderGroupMapper.markCancelled(groupNo, time) > 0;
    }

    @Override
    public boolean markClosed(String groupNo, LocalDateTime time) {
        return orderGroupMapper.markClosed(groupNo, time) > 0;
    }
}
