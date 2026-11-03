package com.four_person.system.api;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.MemberDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 会员积分内部契约，供订单服务在订单完成/售后退款时发放与扣回积分。
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
}
