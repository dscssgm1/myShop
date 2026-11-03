package com.four_person.order.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.order.api.enums.AfterSaleStatus;
import com.four_person.order.domain.model.AfterSale;
import com.four_person.order.domain.repository.AfterSaleRepository;
import com.four_person.order.infrastructure.persistence.mapper.AfterSaleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AfterSaleRepositoryImpl implements AfterSaleRepository {

    private final AfterSaleMapper afterSaleMapper;

    @Override
    public PageResult<AfterSale> pageByUser(Long userId, String status, int page, int size) {
        return doPage(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getUserId, userId)
                .eq(StringUtils.hasText(status), AfterSale::getStatus, status)
                .orderByDesc(AfterSale::getId), page, size);
    }

    @Override
    public PageResult<AfterSale> pageByShop(Long shopId, String status, int page, int size) {
        return doPage(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getShopId, shopId)
                .eq(StringUtils.hasText(status), AfterSale::getStatus, status)
                .orderByDesc(AfterSale::getId), page, size);
    }

    @Override
    public PageResult<AfterSale> pageAll(String status, int page, int size) {
        return doPage(Wrappers.<AfterSale>lambdaQuery()
                .eq(StringUtils.hasText(status), AfterSale::getStatus, status)
                .orderByDesc(AfterSale::getId), page, size);
    }

    @Override
    public Optional<AfterSale> findByNo(String afterSaleNo) {
        return Optional.ofNullable(afterSaleMapper.selectOne(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getAfterSaleNo, afterSaleNo)));
    }

    @Override
    public Optional<AfterSale> findLatestByOrderNo(String orderNo) {
        return Optional.ofNullable(afterSaleMapper.selectOne(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getOrderNo, orderNo)
                .orderByDesc(AfterSale::getId)
                .last("LIMIT 1")));
    }

    @Override
    public Optional<AfterSale> findByReturnTrackingNo(String trackingNo) {
        return Optional.ofNullable(afterSaleMapper.selectOne(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getReturnTrackingNo, trackingNo)
                .orderByDesc(AfterSale::getId)
                .last("LIMIT 1")));
    }

    @Override
    public List<AfterSale> findTimeout(LocalDateTime now) {
        return afterSaleMapper.selectList(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getStatus, AfterSaleStatus.PENDING_MERCHANT.name())
                .isNotNull(AfterSale::getDeadlineAt)
                .lt(AfterSale::getDeadlineAt, now));
    }

    @Override
    public AfterSale save(AfterSale afterSale) {
        if (afterSale.getId() == null) {
            afterSaleMapper.insert(afterSale);
        } else {
            afterSaleMapper.updateById(afterSale);
        }
        return afterSale;
    }

    @Override
    public boolean merchantApprove(Long id, String reason, LocalDateTime time) {
        return afterSaleMapper.merchantApprove(id, reason, time) > 0;
    }

    @Override
    public boolean merchantReject(Long id, String reason, LocalDateTime time) {
        return afterSaleMapper.merchantReject(id, reason, time) > 0;
    }

    @Override
    public boolean escalate(Long id) {
        return afterSaleMapper.escalate(id) > 0;
    }

    @Override
    public boolean autoEscalate(Long id) {
        return afterSaleMapper.autoEscalate(id) > 0;
    }

    @Override
    public boolean platformApprove(Long id, String reason, LocalDateTime time) {
        return afterSaleMapper.platformApprove(id, reason, time) > 0;
    }

    @Override
    public boolean platformReject(Long id, String reason, LocalDateTime time) {
        return afterSaleMapper.platformReject(id, reason, time) > 0;
    }

    @Override
    public boolean markRefunded(Long id, LocalDateTime time) {
        return afterSaleMapper.markRefunded(id, time) > 0;
    }

    @Override
    public boolean markAppealing(Long id, String reason) {
        return afterSaleMapper.markAppealing(id, reason) > 0;
    }

    @Override
    public boolean appealApprove(Long id, String reason, String recoveryOrderNo, LocalDateTime time) {
        return afterSaleMapper.appealApprove(id, reason, recoveryOrderNo, time) > 0;
    }

    @Override
    public boolean appealReject(Long id, String reason, LocalDateTime time) {
        return afterSaleMapper.appealReject(id, reason, time) > 0;
    }

    @Override
    public boolean setReturnShipment(Long id, String trackingNo, String pickupCode) {
        return afterSaleMapper.setReturnShipment(id, trackingNo, pickupCode) > 0;
    }

    @Override
    public boolean setAppointment(Long id, LocalDateTime time) {
        return afterSaleMapper.setAppointment(id, time) > 0;
    }

    private PageResult<AfterSale> doPage(LambdaQueryWrapper<AfterSale> wrapper, int pageNo, int size) {
        int pageIndex = Math.max(pageNo, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<AfterSale> page = afterSaleMapper.selectPage(new Page<>(pageIndex, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageIndex, pageSize);
    }
}
