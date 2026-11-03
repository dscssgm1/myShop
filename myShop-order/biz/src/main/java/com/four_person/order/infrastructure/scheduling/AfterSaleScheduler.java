package com.four_person.order.infrastructure.scheduling;

import com.four_person.order.application.AfterSaleApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描商家审核超时的售后单，自动转平台审核。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSaleScheduler {

    private final AfterSaleApplicationService afterSaleApplicationService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void escalateTimeouts() {
        try {
            afterSaleApplicationService.autoEscalateTimeouts();
        } catch (Exception ex) {
            log.error("售后超时转平台任务执行失败", ex);
        }
    }
}
