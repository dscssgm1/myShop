package com.four_person.file.controller;

import com.four_person.common.api.Result;
import com.four_person.file.dto.FileVO;
import com.four_person.file.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传入口。网关对 /api/files/** 强制 JWT，
 * 上传人可从 X-User-Id 获取（当前业务暂不区分，保留扩展）。
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final MinioStorageService minioStorageService;

    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(minioStorageService.store(file));
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam("objectName") String objectName) {
        minioStorageService.delete(objectName);
        return Result.success();
    }
}
