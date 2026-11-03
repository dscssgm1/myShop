package com.four_person.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类。
 */
@EnableScheduling
@EnableFeignClients(basePackages = {
        "com.four_person.product.api", "com.four_person.discount.api", "com.four_person.message.api",
        "com.four_person.payment.api", "com.four_person.logistics.api", "com.four_person.merchant.api",
        "com.four_person.system.api"})
@MapperScan("com.four_person.order.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.order", "com.four_person.common"})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
