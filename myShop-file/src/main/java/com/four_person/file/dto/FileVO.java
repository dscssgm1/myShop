package com.four_person.file.dto;

import lombok.Data;

/**
 * 上传结果：url 供前端直接展示，objectName 供后续删除时定位对象。
 */
@Data
public class FileVO {

    /** 公网可访问的图片地址 */
    private String url;

    /** MinIO 对象键，如 2026/11/02/uuid.png */
    private String objectName;

    /** 原始文件名 */
    private String originalName;

    /** 文件大小（字节） */
    private Long size;

    /** Content-Type */
    private String contentType;
}
