package com.menswear.orders.dto;

import com.menswear.common.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.List;

public class AdminOrderDtos {

    public record CreateOrderItemRequest(
            @NotNull Long productId,
            @Min(1) int quantity,
            boolean custom,
            Long fabricColorId,
            Long measurementProfileId
    ) {}

    /** The initial payment taken at the point of sale — may be less than the order total (a deposit). */
    public record InitialPaymentRequest(
            @NotNull PaymentMethod method,
            @NotNull @PositiveOrZero Long amountPaisa
    ) {}

    /**
     * whatsappPhone is optional here (unlike the online checkout) — defaults to the
     * customer's phone on file. createdBranchId is optional — if omitted, the acting
     * staff member's own branch is used (see AdminOrderService).
     */
    public record CreateOrderRequest(
            @NotNull Long customerId,
            @NotNull @Valid OrderDtos.AddressDto shippingAddress,
            String whatsappPhone,
            String customerNote,
            Long createdBranchId,
            @NotNull Long dispatchBranchId,
            @PositiveOrZero Long discountPaisa,
            LocalDate expectedDeliveryDate,
            @NotNull @Valid InitialPaymentRequest payment,
            @NotEmpty @Valid List<CreateOrderItemRequest> items
    ) {}

    public record RecordPaymentRequest(
            @NotNull PaymentMethod method,
            @NotNull @jakarta.validation.constraints.Positive Long amountPaisa
    ) {}
}
