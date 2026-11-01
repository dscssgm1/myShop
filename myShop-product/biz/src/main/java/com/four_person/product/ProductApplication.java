package com.four_person.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商品服务启动类。
 */
@MapperScan("com.four_person.product.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.four_person.product", "com.four_person.common"})
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
