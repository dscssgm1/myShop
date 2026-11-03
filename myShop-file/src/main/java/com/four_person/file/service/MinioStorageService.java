package com.four_person.file.service;

import com.four_person.common.exception.BusinessException;
import com.four_person.file.config.MinioProperties;
import com.four_person.file.dto.FileVO;
import com.four_person.file.enums.FileErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /** bucket 就绪标记，避免每次上传都请求 MinIO 检查 */
    private volatile boolean bucketReady = false;

    public FileVO store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "image";
        String extension = extensionOf(originalName);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension) || !contentType.startsWith("image/")) {
            throw new BusinessException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        String objectName = LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID() + "." + extension;
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("图片上传失败: {}", originalName, ex);
            throw new BusinessException(FileErrorCode.UPLOAD_FAILED);
        } finally {
            closeQuietly(file);
        }

        FileVO vo = new FileVO();
        vo.setUrl(publicUrl(objectName));
        vo.setObjectName(objectName);
        vo.setOriginalName(originalName);
        vo.setSize(file.getSize());
        vo.setContentType(contentType);
        return vo;
    }

    public void delete(String objectName) {
        if (!StringUtils.hasText(objectName) || objectName.contains("..")) {
            throw new BusinessException(FileErrorCode.FILE_KEY_INVALID);
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception ex) {
            log.error("图片删除失败: {}", objectName, ex);
            throw new BusinessException(FileErrorCode.UPLOAD_FAILED, "图片删除失败，请稍后重试");
        }
    }

    /**
     * bucket 不存在则创建并设置为公共读，保证浏览器能直接 GET 图片。
     */
    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        String bucket = properties.getBucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(policy)
                    .build());
            log.info("已创建 MinIO bucket {} 并设置公共读", bucket);
        }
        bucketReady = true;
    }

    private String publicUrl(String objectName) {
        String base = StringUtils.hasText(properties.getPublicUrl())
                ? properties.getPublicUrl() : properties.getEndpoint();
        return base + "/" + properties.getBucket() + "/" + objectName;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void closeQuietly(MultipartFile file) {
        try {
            file.getInputStream().close();
        } catch (IOException ignored) {
            // MultipartFile 关闭失败无需处理
        }
    }
}
