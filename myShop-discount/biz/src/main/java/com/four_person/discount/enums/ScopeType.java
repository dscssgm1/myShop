package com.four_person.discount.enums;

/**
 * 活动/优惠券适用范围。
 */
public enum ScopeType {

    /** 全部商品 */
    ALL,

    /** 指定分类 */
    CATEGORY,

    /** 指定商品 */
    PRODUCT,

    /** 本店铺全部商品（商家活动/商家券专用，按归属 shopId 匹配） */
    SHOP
}
