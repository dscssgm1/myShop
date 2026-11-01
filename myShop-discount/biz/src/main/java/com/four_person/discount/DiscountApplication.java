package com.four_person.discount;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 折扣服务启动类。
 */
@EnableFeignClients(basePackages = {"com.four_person.product.api", "com.four_person.order.api"})
@MapperScan("com.four_person.discount.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.discount", "com.four_person.common"})
public class DiscountApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscountApplication.class, args);
    }
}
