package com.four_person.discount.api.dto;

import lombok.Data;

@Data
public class EnrollmentReviewRequest {

    /** 驳回原因（通过时忽略） */
    private String reason;
}
