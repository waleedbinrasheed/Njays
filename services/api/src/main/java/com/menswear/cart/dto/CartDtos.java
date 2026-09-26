package com.menswear.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CartDtos {

    public record AddItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            boolean custom,
            Long fabricColorId,
            Long measurementProfileId
    ) {}

    public record CartItemResponse(
            Long id,
            Long productId,
            String productName,
            int quantity,
            boolean custom,
            Long fabricColorId,
            Long measurementProfileId,
            Long unitPricePaisa,
            Long lineTotalPaisa
    ) {}

    public record CartResponse(Long id, List<CartItemResponse> items, Long subtotalPaisa) {}
}
