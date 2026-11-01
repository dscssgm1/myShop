package com.four_person.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 支付服务启动类。
 */
@EnableFeignClients(basePackages = "com.four_person.order.api")
@MapperScan("com.four_person.payment.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.payment", "com.four_person.common"})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
