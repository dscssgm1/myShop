package com.four_person.discount.application.port;

/**
 * 店铺信息查询端口，基础设施层用商家服务 Feign 契约实现。
 */
public interface ShopQueryPort {

    /** 店铺名称，查询失败或店铺不存在时返回「店铺#id」兜底 */
    String shopName(Long shopId);
}
