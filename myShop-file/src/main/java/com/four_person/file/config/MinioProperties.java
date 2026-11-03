package com.four_person.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 连接配置，默认指向 docker-compose 中的 myshop-minio。
 */
@Data
@ConfigurationProperties(prefix = "myshop.minio")
public class MinioProperties {

    /** S3 API 地址，上传/删除走这里 */
    private String endpoint = "http://127.0.0.1:9000";

    /** 浏览器访问图片用的地址，默认与 endpoint 相同；上了域名/反代后单独配置 */
    private String publicUrl = "http://127.0.0.1:9000";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    private String bucket = "myshop";
}
