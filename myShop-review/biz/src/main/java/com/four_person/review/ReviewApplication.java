package com.four_person.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.four_person.system.api")
@MapperScan("com.four_person.review.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.review", "com.four_person.common"})
public class ReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewApplication.class, args);
    }
}
