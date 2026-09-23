package com.menswear.identity.dto;

public class UserDtos {

    public record UserSummary(
            Long id,
            String fullName,
            String email,
            String role,
            Long branchId,
            boolean enabled
    ) {}
}
