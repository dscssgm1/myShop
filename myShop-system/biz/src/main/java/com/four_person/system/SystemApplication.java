package com.four_person.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 系统设置服务启动类。
 */
@EnableFeignClients(basePackages = {"com.four_person.product.api", "com.four_person.order.api",
        "com.four_person.discount.api"})
@MapperScan("com.four_person.system.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.system", "com.four_person.common"})
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
