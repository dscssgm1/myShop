package com.four_person.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.four_person.platform.infrastructure.persistence.mapper")
@EnableFeignClients(basePackages = "com.four_person.message.api")
@SpringBootApplication(scanBasePackages = {"com.four_person.platform", "com.four_person.common"})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
