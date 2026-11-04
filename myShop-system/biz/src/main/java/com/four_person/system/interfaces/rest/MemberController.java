package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.system.api.dto.MemberDtos;
import com.four_person.system.application.MembershipApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MembershipApplicationService membershipApplicationService;

    /** 当前用户的会员等级、积分与成长值 */
    @GetMapping("/profile")
    public Result<MemberDtos.MemberProfileVO> profile() {
        return Result.success(membershipApplicationService.profile(SecurityUtils.currentUserId()));
    }

    @GetMapping("/points/logs")
    public Result<List<MemberDtos.PointsLogVO>> logs(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(membershipApplicationService.logs(SecurityUtils.currentUserId(), limit));
    }

    // ==================== 平台配置中心：会员等级 ====================

    @GetMapping("/levels")
    public Result<List<MemberDtos.MemberLevelVO>> levels() {
        AccessGuard.requirePerm("admin:config:member");
        return Result.success(membershipApplicationService.levels());
    }

    @PostMapping("/levels")
    public Result<MemberDtos.MemberLevelVO> createLevel(@Valid @RequestBody MemberDtos.MemberLevelRequest request) {
        AccessGuard.requirePerm("admin:config:member");
        return Result.success("会员等级已新增", membershipApplicationService.createLevel(request));
    }

    @PutMapping("/levels/{id}")
    public Result<MemberDtos.MemberLevelVO> updateLevel(@PathVariable Long id,
                                                        @Valid @RequestBody MemberDtos.MemberLevelRequest request) {
        AccessGuard.requirePerm("admin:config:member");
        return Result.success("会员等级已更新", membershipApplicationService.updateLevel(id, request));
    }

    @DeleteMapping("/levels/{id}")
    public Result<Void> deleteLevel(@PathVariable Long id) {
        AccessGuard.requirePerm("admin:config:member");
        membershipApplicationService.deleteLevel(id);
        return Result.success("会员等级已删除", null);
    }
}
