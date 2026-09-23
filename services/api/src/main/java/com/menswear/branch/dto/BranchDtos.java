package com.menswear.branch.dto;

import jakarta.validation.constraints.NotBlank;

public class BranchDtos {

    public record CreateBranchRequest(
            @NotBlank String name,
            String address,
            String phone
    ) {}

    public record UpdateBranchRequest(
            @NotBlank String name,
            String address,
            String phone,
            boolean active
    ) {}

    public record BranchResponse(
            Long id,
            String name,
            String address,
            String phone,
            boolean active
    ) {}
}
