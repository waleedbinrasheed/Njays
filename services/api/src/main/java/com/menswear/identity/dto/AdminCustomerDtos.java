package com.menswear.identity.dto;

import com.menswear.measurements.dto.MeasurementDtos;
import com.menswear.orders.dto.OrderDtos;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AdminCustomerDtos {

    public record CustomerSummary(Long id, String fullName, String phone, String email) {}

    public record CreateWalkInRequest(
            @NotBlank String fullName,
            @NotBlank String phone,
            String email
    ) {}

    public record CustomerDetailResponse(
            Long id,
            String fullName,
            String phone,
            String email,
            List<MeasurementDtos.Response> measurements,
            List<OrderDtos.OrderResponse> orders,
            long outstandingBalancePaisa
    ) {}
}
