package com.menswear.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            Long branchId
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record MeResponse(
            Long id,
            String fullName,
            String email,
            String role,
            Long branchId
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            MeResponse user
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record MessageResponse(String message) {}
}
