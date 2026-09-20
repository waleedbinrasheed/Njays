package com.menswear.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class AdminOrderDtos {

    public record CreateOrderItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            boolean custom,
            Long fabricColorId,
            Long measurementProfileId
    ) {}

    /** whatsappPhone is optional here (unlike the online checkout) — defaults to the customer's phone on file. */
    public record CreateOrderRequest(
            @NotNull Long customerId,
            @NotNull @Valid OrderDtos.AddressDto shippingAddress,
            String whatsappPhone,
            String customerNote,
            @NotEmpty @Valid List<CreateOrderItemRequest> items
    ) {}
}
