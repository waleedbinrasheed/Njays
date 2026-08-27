package com.menswear.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String fullName,
            String phone
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {}

    public record ForgotPasswordResponse(String message, String resetLink) {}

    public record MessageResponse(String message) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            UserResponse user
    ) {}

    public record UserResponse(Long id, String email, String fullName, String phone, String role) {}
}
