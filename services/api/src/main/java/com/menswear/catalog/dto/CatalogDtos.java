package com.menswear.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CatalogDtos {

    public record CategoryResponse(Long id, String name, String slug, String description) {}

    public record ProductImageResponse(String url, String altText, int sortOrder) {}

    public record ProductResponse(
            Long id,
            String name,
            String slug,
            String description,
            Long basePricePaisa,
            String currency,
            boolean supportsCustom,
            Long categoryId,
            List<ProductImageResponse> images
    ) {}

    public record FabricColorResponse(Long id, String code, String name, String hexColor) {}

    public record FabricTierResponse(
            Long id,
            String code,
            String name,
            Long surchargePaisa,
            List<FabricColorResponse> colors
    ) {}

    public record ProductImageRequest(
            @NotBlank String url,
            String altText,
            Integer sortOrder
    ) {}

    public record CreateProductRequest(
            @NotBlank String name,
            @NotBlank String slug,
            String description,
            @NotNull @Min(1) Long basePricePaisa,
            Long categoryId,
            Boolean supportsCustom,
            Boolean active,
            @NotEmpty @Valid List<ProductImageRequest> images
    ) {}
}
