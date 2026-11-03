package com.four_person.order.infrastructure.scheduling;

import com.four_person.order.application.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描物流签收已满期仍未确认收货的订单，自动完成交易。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentAutoCompleteScheduler {

    private final OrderApplicationService orderApplicationService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void autoComplete() {
        try {
            orderApplicationService.autoCompleteSignedOrders();
        } catch (Exception ex) {
            log.error("签收自动完成订单任务执行失败", ex);
        }
    }
}
