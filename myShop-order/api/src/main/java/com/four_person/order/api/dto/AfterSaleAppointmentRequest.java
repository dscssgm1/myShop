package com.four_person.order.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户预约售后退货上门取件时间（仅限两天以内）。
 */
@Data
public class AfterSaleAppointmentRequest {

    @NotNull(message = "请选择上门取件时间")
    private LocalDateTime appointmentTime;
}
