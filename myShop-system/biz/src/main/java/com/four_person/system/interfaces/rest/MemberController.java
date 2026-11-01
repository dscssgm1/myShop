package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.MemberDtos;
import com.four_person.system.application.MembershipApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
