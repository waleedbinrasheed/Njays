package com.menswear.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

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

    public record DispatchCostRuleRequest(
            @NotNull Long sourceBranchId,
            @NotNull Long destinationBranchId,
            @NotNull @PositiveOrZero Long costPaisa
    ) {}

    public record DispatchCostRuleResponse(
            Long id,
            Long sourceBranchId,
            String sourceBranchName,
            Long destinationBranchId,
            String destinationBranchName,
            Long costPaisa
    ) {}

    public record DispatchCostSettingsResponse(
            long defaultCostPaisa,
            java.util.List<DispatchCostRuleResponse> rules
    ) {}
}
