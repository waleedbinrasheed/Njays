package com.menswear.measurements.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class MeasurementDtos {

    public record UpsertRequest(
            @NotBlank String name,
            String unit,
            BigDecimal kameezLength,
            BigDecimal chest,
            BigDecimal waist,
            BigDecimal hip,
            BigDecimal shoulder,
            BigDecimal sleeveLength,
            BigDecimal collarLength,
            BigDecimal shalwarLength,
            BigDecimal shalwarBottom,
            String backStyle,
            String sleeveStyle,
            String buttonStyle,
            String collarStyle,
            String cuffStyle,
            String notes,
            Boolean isDefault
    ) {}

    public record Response(
            Long id,
            String name,
            String unit,
            BigDecimal kameezLength,
            BigDecimal chest,
            BigDecimal waist,
            BigDecimal hip,
            BigDecimal shoulder,
            BigDecimal sleeveLength,
            BigDecimal collarLength,
            BigDecimal shalwarLength,
            BigDecimal shalwarBottom,
            String backStyle,
            String sleeveStyle,
            String buttonStyle,
            String collarStyle,
            String cuffStyle,
            String notes,
            boolean isDefault
    ) {}
}
