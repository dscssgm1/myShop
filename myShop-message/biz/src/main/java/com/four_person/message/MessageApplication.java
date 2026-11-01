package com.four_person.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.four_person.message.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.message", "com.four_person.common"})
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
