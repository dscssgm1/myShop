package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.AuditApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditApplicationService auditApplicationService;

    /** 提交审核（各业务服务 / 买家发起） */
    @PostMapping
    public Result<PlatformDtos.AuditVO> create(@Valid @RequestBody PlatformDtos.AuditCreateRequest request) {
        return Result.success("审核已提交", auditApplicationService.create(request));
    }

    @GetMapping
    public Result<PageResult<PlatformDtos.AuditVO>> page(@RequestParam(required = false) String type,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return Result.success(auditApplicationService.page(type, status, page, size));
    }

    @GetMapping("/mine")
    public Result<List<PlatformDtos.AuditVO>> mine() {
        return Result.success(auditApplicationService.mine());
    }

    /** 第一级审核（平台业务员）：物流入驻申请初审 */
    @PutMapping("/{id}/first-approve")
    public Result<PlatformDtos.AuditVO> firstApprove(@PathVariable Long id,
                                                     @RequestBody(required = false) PlatformDtos.AuditActionRequest request) {
        return Result.success("初审已通过，转平台管理员终审",
                auditApplicationService.firstApprove(id, request == null ? null : request.getReason()));
    }

    @PutMapping("/{id}/approve")
    public Result<PlatformDtos.AuditVO> approve(@PathVariable Long id,
                                                @RequestBody(required = false) PlatformDtos.AuditActionRequest request) {
        return Result.success("已通过", auditApplicationService.approve(id, request == null ? null : request.getReason()));
    }

    @PutMapping("/{id}/reject")
    public Result<PlatformDtos.AuditVO> reject(@PathVariable Long id,
                                               @RequestBody(required = false) PlatformDtos.AuditActionRequest request) {
        return Result.success("已驳回", auditApplicationService.reject(id, request == null ? null : request.getReason()));
    }
}
