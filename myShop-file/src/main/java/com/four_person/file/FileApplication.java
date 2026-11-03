package com.four_person.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 文件服务：无数据库依赖，图片统一写入 MinIO。
 */
@SpringBootApplication(scanBasePackages = {"com.four_person.file", "com.four_person.common"})
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
