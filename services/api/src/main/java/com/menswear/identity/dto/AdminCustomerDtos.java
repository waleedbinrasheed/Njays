package com.menswear.identity.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminCustomerDtos {

    public record CustomerSummary(Long id, String fullName, String phone, String email) {}

    public record CreateWalkInRequest(
            @NotBlank String fullName,
            @NotBlank String phone,
            String email
    ) {}
}
