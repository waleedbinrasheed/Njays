package com.menswear.legacyimport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class LegacyImportDtos {

    public record ItemRequest(
            String fabricName,
            Double meters,
            Long fabricRatePaisa,
            Long fabricTotalPaisa,
            Long stitchPaisa
    ) {}

    public record OrderRequest(
            @NotBlank String legacyRef,
            @NotBlank String customerName,
            @NotBlank String rawPhone,
            String slipNumber,
            Integer suitCount,
            String branch,
            /** ISO yyyy-MM-dd, or null if unknown. */
            String orderDate,
            @NotNull Long totalPaisa,
            Long paidPaisa,
            /** Raw status string from the ledger: "Delivered", "Correction", or blank. */
            String status,
            @NotEmpty @Valid List<ItemRequest> items
    ) {}

    public record ImportRequest(@NotEmpty @Valid List<OrderRequest> orders) {}

    public record ImportResult(
            int customersCreated,
            int customersMatched,
            int ordersCreated,
            int ordersSkipped,
            List<String> warnings
    ) {}
}
