package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.LogisticsApplyApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/logistics-apply")
@RequiredArgsConstructor
public class LogisticsApplyController {

    private final LogisticsApplyApplicationService logisticsApplyApplicationService;

    /** 用户提交物流入驻申请（两级审核：业务员初审 → 管理员终审） */
    @PostMapping
    public Result<PlatformDtos.AuditVO> apply(@Valid @RequestBody PlatformDtos.LogisticsApplyRequest request) {
        return Result.success("入驻申请已提交，等待平台业务员初审", logisticsApplyApplicationService.apply(request));
    }

    /** 我的入驻申请及审核进度 */
    @GetMapping("/mine")
    public Result<List<PlatformDtos.AuditVO>> mine() {
        return Result.success(logisticsApplyApplicationService.mine());
    }
}
