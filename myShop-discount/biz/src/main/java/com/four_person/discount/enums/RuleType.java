package com.four_person.discount.enums;

/**
 * 优惠计算规则类型。
 */
public enum RuleType {

    /** 满减：满足门槛减固定金额，perEvery 大于 0 时按每满计算 */
    FULL_REDUCTION,

    /** 折扣：按折扣率计算，可设置优惠上限 */
    DISCOUNT
}
