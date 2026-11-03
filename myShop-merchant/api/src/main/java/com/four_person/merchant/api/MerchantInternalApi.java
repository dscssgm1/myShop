package com.four_person.merchant.api;

import com.four_person.common.api.Result;
import com.four_person.merchant.api.dto.MerchantDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商家服务内部契约，供订单服务创建售后退货运单时读取店铺地址。
 */
@FeignClient(name = "myshop-merchant", url = "${myshop.clients.merchant-url:}")
public interface MerchantInternalApi {

    @GetMapping("/internal/shops/{id}")
    Result<MerchantDtos.ShopVO> shop(@PathVariable("id") Long id);
}
