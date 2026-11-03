package com.four_person.order.domain.repository;

import com.four_person.common.api.PageResult;
import com.four_person.order.domain.model.AfterSale;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AfterSaleRepository {

    PageResult<AfterSale> pageByUser(Long userId, String status, int page, int size);

    PageResult<AfterSale> pageByShop(Long shopId, String status, int page, int size);

    PageResult<AfterSale> pageAll(String status, int page, int size);

    Optional<AfterSale> findByNo(String afterSaleNo);

    Optional<AfterSale> findLatestByOrderNo(String orderNo);

    /** 按退货运单号定位售后单（物流签收回调用） */
    Optional<AfterSale> findByReturnTrackingNo(String trackingNo);

    List<AfterSale> findTimeout(LocalDateTime now);

    AfterSale save(AfterSale afterSale);

    boolean merchantApprove(Long id, String reason, LocalDateTime time);

    boolean merchantReject(Long id, String reason, LocalDateTime time);

    boolean escalate(Long id);

    boolean autoEscalate(Long id);

    boolean platformApprove(Long id, String reason, LocalDateTime time);

    boolean platformReject(Long id, String reason, LocalDateTime time);

    boolean markRefunded(Long id, LocalDateTime time);

    boolean markAppealing(Long id, String reason);

    boolean appealApprove(Long id, String reason, String recoveryOrderNo, LocalDateTime time);

    boolean appealReject(Long id, String reason, LocalDateTime time);

    boolean setReturnShipment(Long id, String trackingNo, String pickupCode);

    boolean setAppointment(Long id, LocalDateTime time);
}
