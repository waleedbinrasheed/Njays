package com.menswear.orders.dto;

import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class OrderDtos {

    public record AddressDto(
            @NotBlank String line1,
            String line2,
            @NotBlank String city,
            String province,
            String postalCode,
            String country
    ) {}

    public record CreateOrderRequest(
            @NotNull AddressDto shippingAddress,
            @NotBlank String whatsappPhone,
            String customerNote
    ) {}

    public record StatusHistoryResponse(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String note,
            Instant createdAt
    ) {}

    public record OrderItemResponse(
            Long productId,
            String productName,
            int quantity,
            boolean custom,
            String fabricLabel,
            Long unitPricePaisa,
            Long lineTotalPaisa
    ) {}

    public record OrderResponse(
            Long id,
            String publicCode,
            OrderType orderType,
            OrderStatus status,
            String currency,
            Long subtotalPaisa,
            Long discountPaisa,
            Long dispatchCostPaisa,
            Long totalPaisa,
            Long amountPaidPaisa,
            Long balanceDuePaisa,
            String whatsappPhone,
            String customerNote,
            Long createdBranchId,
            String createdBranchName,
            Long dispatchBranchId,
            String dispatchBranchName,
            LocalDate expectedDeliveryDate,
            List<OrderItemResponse> items,
            List<StatusHistoryResponse> timeline,
            Instant createdAt
    ) {}

    public record UpdateStatusRequest(
            @NotNull OrderStatus status,
            String note
    ) {}

    public record TrackResponse(
            String publicCode,
            OrderStatus status,
            List<StatusHistoryResponse> timeline
    ) {}
}
