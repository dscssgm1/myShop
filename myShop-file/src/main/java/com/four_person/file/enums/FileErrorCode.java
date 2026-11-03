package com.four_person.file.enums;

import com.four_person.common.exception.ErrorCode;

/**
 * 文件服务错误码，4101x 段。
 */
public enum FileErrorCode implements ErrorCode {

    FILE_EMPTY(41001, "上传文件为空"),
    FILE_TYPE_NOT_ALLOWED(41002, "仅支持 jpg/jpeg/png/gif/webp/bmp 图片"),
    FILE_TOO_LARGE(41003, "图片大小不能超过 5MB"),
    FILE_KEY_INVALID(41004, "文件标识不合法"),
    UPLOAD_FAILED(41500, "图片上传失败，请稍后重试");

    private final int code;
    private final String message;

    FileErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
