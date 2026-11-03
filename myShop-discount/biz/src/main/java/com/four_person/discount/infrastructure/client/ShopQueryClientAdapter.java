package com.four_person.discount.infrastructure.client;

import com.four_person.common.api.Results;
import com.four_person.discount.application.port.ShopQueryPort;
import com.four_person.merchant.api.MerchantInternalApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopQueryClientAdapter implements ShopQueryPort {

    private final MerchantInternalApi merchantInternalApi;

    @Override
    public String shopName(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return "";
        }
        try {
            var shop = Results.data(merchantInternalApi.shop(shopId));
            if (shop != null && shop.getName() != null && !shop.getName().isBlank()) {
                return shop.getName();
            }
        } catch (Exception ex) {
            log.warn("查询店铺 {} 名称失败，使用兜底名称", shopId, ex);
        }
        return "店铺#" + shopId;
    }
}
