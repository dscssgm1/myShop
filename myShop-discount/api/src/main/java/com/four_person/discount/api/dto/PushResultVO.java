package com.four_person.discount.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class PushResultVO {

    private Long userId;

    private String segment;

    private String segmentText;

    private String valueTier;

    private List<String> reasons;

    private List<PromotionVO> promotions;

    private List<CouponTemplateVO> coupons;
}
