package com.four_person.system.api;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.MemberDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会员积分内部契约，供订单服务算价/下单抵现/退款退回积分。
 */
@FeignClient(name = "myshop-system", url = "${myshop.clients.system-url:}")
public interface MemberInternalApi {

    /** 订单完成发放积分，返回实际获得积分 */
    @PostMapping("/internal/points/earn")
    Result<Integer> earn(@RequestBody MemberDtos.EarnPointsRequest request);

    /** 售后退款扣回该订单发放的积分，返回实际扣回数 */
    @PostMapping("/internal/points/refund-earn")
    Result<Integer> refundEarn(@RequestBody MemberDtos.EarnPointsRequest request);

    /** 按条直发固定积分（评价奖励等场景），bizNo 幂等，返回实际入账积分 */
    @PostMapping("/internal/points/earn-flat")
    Result<Integer> earnFlat(@RequestBody MemberDtos.EarnFlatRequest request);

    // ==================== 下单积分抵现 ====================

    /** 积分余额（结算页算可抵上限用） */
    @GetMapping("/internal/points/balance")
    Result<Integer> balance(@RequestParam("userId") Long userId);

    /** 扣减积分（下单抵现），bizNo 幂等 */
    @PostMapping("/internal/points/spend")
    Result<Void> spend(@RequestBody MemberDtos.SpendPointsRequest request);

    /** 退回抵现扣减的积分（取消/退款），按业务单号幂等，返回实际退回数 */
    @PostMapping("/internal/points/refund-spend")
    Result<Integer> refundSpend(@RequestBody MemberDtos.RefundSpendRequest request);
}
