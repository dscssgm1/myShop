package com.four_person.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类。
 */
@EnableFeignClients(basePackages = {"com.four_person.product.api", "com.four_person.discount.api"})
@MapperScan("com.four_person.order.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.order", "com.four_person.common"})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
