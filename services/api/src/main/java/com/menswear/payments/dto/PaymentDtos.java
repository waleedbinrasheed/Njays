package com.menswear.payments.dto;

import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public class PaymentDtos {

    public record CreatePaymentRequest(
            @NotNull PaymentMethod method,
            @NotBlank String idempotencyKey,
            String proofUrl
    ) {}

    public record ProofRequest(@NotBlank String proofUrl) {}

    public record BankInstructions(
            String accountTitle,
            String accountNumber,
            String bankName,
            String iban,
            String reference,
            Long amountPaisa,
            String currency
    ) {}

    public record JazzCashRedirect(
            String actionUrl,
            Map<String, String> fields,
            boolean sandbox,
            Instant expiresAt
    ) {}

    public record PaymentResponse(
            Long id,
            Long orderId,
            String orderPublicCode,
            PaymentMethod method,
            PaymentStatus status,
            Long amountPaisa,
            String currency,
            String providerRef,
            String proofUrl,
            String failureReason,
            Instant expiresAt,
            Instant confirmedAt,
            BankInstructions bankInstructions,
            JazzCashRedirect jazzCashRedirect,
            Instant createdAt
    ) {}

    public record WebhookAck(boolean ok, String status, Long paymentId, String orderPublicCode, String message) {}
}
