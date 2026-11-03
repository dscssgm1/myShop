package com.four_person.logistics;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.four_person.order.api")
@MapperScan("com.four_person.logistics.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.logistics", "com.four_person.common"})
public class LogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsApplication.class, args);
    }
}
