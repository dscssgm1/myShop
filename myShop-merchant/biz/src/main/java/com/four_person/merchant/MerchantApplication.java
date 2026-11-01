package com.four_person.merchant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.four_person.merchant.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.merchant", "com.four_person.common"})
public class MerchantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
