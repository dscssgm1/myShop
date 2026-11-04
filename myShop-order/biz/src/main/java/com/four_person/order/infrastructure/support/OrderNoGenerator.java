package com.four_person.order.infrastructure.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号：MS + 毫秒级时间 + 3 位随机数。
 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public String next() {
        return "MS" + LocalDateTime.now().format(FORMATTER)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    public String nextAfterSale() {
        return "AS" + LocalDateTime.now().format(FORMATTER)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    /** 跨店合单订单组号：MG + 毫秒级时间 + 3 位随机数 */
    public String nextGroup() {
        return "MG" + LocalDateTime.now().format(FORMATTER)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
