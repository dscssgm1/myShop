package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.MemberDtos;
import com.four_person.system.api.dto.UserDtos;
import com.four_person.system.application.MembershipApplicationService;
import com.four_person.system.application.UserApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部接口，不经过网关对外暴露。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserApplicationService userApplicationService;
    private final MembershipApplicationService membershipApplicationService;

    @GetMapping("/users/{id}")
    public Result<UserDtos.UserVO> user(@PathVariable Long id) {
        return Result.success(userApplicationService.profile(id));
    }

    /** 订单完成等场景由其他服务调用，按消费金额发放积分 */
    @PostMapping("/points/earn")
    public Result<Integer> earn(@RequestBody MemberDtos.EarnPointsRequest request) {
        return Result.success(membershipApplicationService.earn(
                request.getUserId(), request.getAmount(), request.getRemark(), request.getBizNo()));
    }

    @PostMapping("/points/spend")
    public Result<Void> spend(@RequestBody MemberDtos.SpendPointsRequest request) {
        membershipApplicationService.spend(
                request.getUserId(), request.getPoints() == null ? 0 : request.getPoints(),
                request.getBizNo(), request.getRemark());
        return Result.success();
    }

    /** 结算页算可抵上限：查积分余额 */
    @GetMapping("/points/balance")
    public Result<Integer> balance(@RequestParam("userId") Long userId) {
        return Result.success(membershipApplicationService.balance(userId));
    }

    /** 取消订单/退款：退回抵现扣减的积分，按业务单号幂等，返回实际退回数 */
    @PostMapping("/points/refund-spend")
    public Result<Integer> refundSpend(@RequestBody MemberDtos.RefundSpendRequest request) {
        return Result.success(membershipApplicationService.refundSpend(
                request.getUserId(), request.getBizNo(), request.getRemark()));
    }

    /** 售后退款扣回该订单发放的积分，返回实际扣回数 */
    @PostMapping("/points/refund-earn")
    public Result<Integer> refundEarn(@RequestBody MemberDtos.EarnPointsRequest request) {
        return Result.success(membershipApplicationService.refundEarn(
                request.getUserId(), request.getBizNo(), request.getRemark()));
    }

    /** 评价等其他服务按条直发固定积分，bizNo 幂等 */
    @PostMapping("/points/earn-flat")
    public Result<Integer> earnFlat(@RequestBody MemberDtos.EarnFlatRequest request) {
        return Result.success(membershipApplicationService.earnFlat(
                request.getUserId(), request.getPoints(), request.getRemark(), request.getBizNo()));
    }

    /** 扣回已发放的积分（删除评价），按 bizNo 幂等，返回实际扣回数 */
    @PostMapping("/points/revoke-earn")
    public Result<Integer> revokeEarn(@RequestBody MemberDtos.RevokeEarnRequest request) {
        return Result.success(membershipApplicationService.revokeEarn(
                request.getUserId(), request.getBizNo(), request.getRemark()));
    }
}
