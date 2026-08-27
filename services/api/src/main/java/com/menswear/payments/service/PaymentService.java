package com.menswear.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.config.MenswearProperties;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.orders.service.OrderService;
import com.menswear.payments.dto.PaymentDtos;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.jazzcash.JazzCashCrypto;
import com.menswear.payments.repo.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final DateTimeFormatter JC_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final EnumSet<PaymentStatus> OPEN = EnumSet.of(
            PaymentStatus.PENDING,
            PaymentStatus.AWAITING_CONFIRMATION
    );

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final MenswearProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            OrderService orderService,
            MenswearProperties properties,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentDtos.PaymentResponse create(Long orderId, PaymentDtos.CreatePaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(this::toDto)
                .orElseGet(() -> createNew(orderId, request));
    }

    private PaymentDtos.PaymentResponse createNew(Long orderId, PaymentDtos.CreatePaymentRequest request) {
        ShopOrder order = orderRepository.findByIdAndUserId(orderId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Order not found"));

        if (order.getStatus() == OrderStatus.PAYMENT_CONFIRMED
                || paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.COMPLETED)) {
            throw new BadRequestException("Order is already paid");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot pay a cancelled order");
        }

        var existingOpen = paymentRepository.findFirstByOrderIdAndMethodAndStatusInOrderByCreatedAtDesc(
                orderId, request.method(), OPEN
        );
        if (existingOpen.isPresent()) {
            Payment open = existingOpen.get();
            if (open.getAmountPaisa().equals(order.getTotalPaisa())) {
                if (request.proofUrl() != null && !request.proofUrl().isBlank()
                        && request.method() == PaymentMethod.BANK_TRANSFER) {
                    open.setProofUrl(request.proofUrl());
                    open.setStatus(PaymentStatus.AWAITING_CONFIRMATION);
                    paymentRepository.save(open);
                }
                return toDto(open);
            }
            open.setStatus(PaymentStatus.CANCELLED);
            open.setFailureReason("Superseded by new payment attempt");
            paymentRepository.save(open);
        }

        PaymentStatus status = switch (request.method()) {
            case COD -> PaymentStatus.PENDING;
            case BANK_TRANSFER -> PaymentStatus.AWAITING_CONFIRMATION;
            case JAZZCASH -> PaymentStatus.PENDING;
        };

        Instant expiresAt = request.method() == PaymentMethod.JAZZCASH
                ? Instant.now().plusSeconds(3600)
                : null;

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .method(request.method())
                .status(status)
                .amountPaisa(order.getTotalPaisa())
                .currency(order.getCurrency())
                .idempotencyKey(request.idempotencyKey())
                .proofUrl(request.proofUrl())
                .providerRef(request.method() == PaymentMethod.JAZZCASH ? nextJazzCashTxnRef() : null)
                .expiresAt(expiresAt)
                .build();

        Payment saved = paymentRepository.save(payment);
        paymentRepository.cancelOpenPayments(
                orderId,
                request.method(),
                OPEN,
                saved.getId(),
                "Superseded by payment #" + saved.getId()
        );
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentResponse> listForOrder(Long orderId) {
        orderRepository.findByIdAndUserId(orderId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentResponse getOwned(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        orderRepository.findByIdAndUserId(payment.getOrderId(), SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return toDto(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentResponse> adminPending() {
        return paymentRepository.findByStatusInOrderByCreatedAtDesc(
                        List.of(PaymentStatus.PENDING, PaymentStatus.AWAITING_CONFIRMATION)
                ).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PaymentDtos.PaymentResponse attachProof(Long paymentId, PaymentDtos.ProofRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        orderRepository.findByIdAndUserId(payment.getOrderId(), SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new BadRequestException("Proof only applies to bank transfer");
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return toDto(payment);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.FAILED) {
            throw new BadRequestException("This payment can no longer accept proof");
        }
        String url = request.proofUrl().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BadRequestException("Proof must be an http(s) URL");
        }
        payment.setProofUrl(url);
        payment.setStatus(PaymentStatus.AWAITING_CONFIRMATION);
        payment.setFailureReason(null);
        return toDto(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentDtos.PaymentResponse confirmAdmin(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return toDto(payment);
        }
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER && payment.getMethod() != PaymentMethod.COD) {
            throw new BadRequestException("Only bank transfer / COD can be manually confirmed");
        }
        completePayment(payment, SecurityUtils.currentUserId(), "Admin confirmed");
        return toDto(payment);
    }

    @Transactional
    public PaymentDtos.WebhookAck handleJazzCashCallback(Map<String, String> payload) {
        Payment payment = applyJazzCashPayload(payload);
        ShopOrder order = orderRepository.findById(payment.getOrderId()).orElse(null);
        return new PaymentDtos.WebhookAck(
                payment.getStatus() == PaymentStatus.COMPLETED,
                payment.getStatus().name(),
                payment.getId(),
                order != null ? order.getPublicCode() : null,
                payment.getFailureReason() != null
                        ? payment.getFailureReason()
                        : (payment.getStatus() == PaymentStatus.COMPLETED ? "Payment successful" : "Payment updated")
        );
    }

    public String frontendReturnUrl(PaymentDtos.WebhookAck ack) {
        String base = properties.payments().jazzcash().frontendReturnUrl();
        if (base == null || base.isBlank()) {
            String app = properties.frontend() != null ? properties.frontend().appUrl() : "http://localhost:3000";
            base = app.replaceAll("/$", "") + "/checkout/jazzcash/return";
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(base)
                .queryParam("ok", ack.ok())
                .queryParam("status", ack.status() != null ? ack.status() : "UNKNOWN");
        if (ack.paymentId() != null) {
            b.queryParam("paymentId", ack.paymentId());
        }
        if (ack.orderPublicCode() != null && !ack.orderPublicCode().isBlank()) {
            b.queryParam("order", ack.orderPublicCode());
        }
        if (ack.message() != null && !ack.message().isBlank()) {
            b.queryParam("message", ack.message());
        }
        return b.build().encode().toUriString();
    }

    private Payment applyJazzCashPayload(Map<String, String> payload) {
        var jc = properties.payments().jazzcash();
        boolean requireHash = jc.requireSecureHash() && !(jc.sandbox() && "success".equalsIgnoreCase(payload.get("status")));
        if (requireHash) {
            if (!JazzCashCrypto.verify(payload, jc.integritySalt())) {
                log.warn("JazzCash callback rejected: invalid secure hash for txn={}",
                        payload.getOrDefault("pp_TxnRefNo", "?"));
                throw new BadRequestException("Invalid JazzCash secure hash");
            }
        } else if (payload.containsKey("pp_SecureHash") || payload.containsKey("pp_secureHash")) {
            if (!JazzCashCrypto.verify(payload, jc.integritySalt())) {
                log.warn("JazzCash callback hash mismatch (non-strict mode still rejected forged hash)");
                throw new BadRequestException("Invalid JazzCash secure hash");
            }
        }

        String txnRef = first(payload, "pp_TxnRefNo", "pp_TxnRefno");
        String billRef = first(payload, "pp_BillReference");
        Payment payment = null;
        if (txnRef != null) {
            payment = paymentRepository.findByProviderRefForUpdate(txnRef).orElse(null);
        }
        if (payment == null) {
            String paymentIdRaw = first(payload, "ppmpf_1");
            if (paymentIdRaw != null && paymentIdRaw.matches("\\d+")) {
                payment = paymentRepository.findByIdForUpdate(Long.parseLong(paymentIdRaw)).orElse(null);
            }
        }
        if (payment == null && billRef != null) {
            // Legacy fallback: older provider refs used JC-{publicCode}
            payment = paymentRepository.findByProviderRefForUpdate("JC-" + billRef).orElse(null);
        }
        if (payment == null) {
            throw new NotFoundException("Payment not found for JazzCash txn");
        }
        if (payment.getMethod() != PaymentMethod.JAZZCASH) {
            throw new BadRequestException("Payment is not JazzCash");
        }

        try {
            payment.setRawPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            payment.setRawPayload(payload.toString());
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new BadRequestException("Payment was cancelled");
        }

        String responseCode = first(payload, "pp_ResponseCode");
        boolean simulatedOk = jc.sandbox() && "success".equalsIgnoreCase(payload.get("status"));
        boolean success = "000".equals(responseCode) || simulatedOk;

        if (success) {
            validateJazzCashAmount(payload, payment);
            completePayment(payment, null, "JazzCash " + (responseCode != null ? responseCode : "simulated"));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(first(payload, "pp_ResponseMessage", "pp_ResponseCode"));
            if (payment.getFailureReason() == null) {
                payment.setFailureReason("JazzCash payment failed");
            }
            paymentRepository.save(payment);
            log.info("JazzCash payment {} failed code={} msg={}",
                    payment.getId(), responseCode, payment.getFailureReason());
        }
        return payment;
    }

    private void validateJazzCashAmount(Map<String, String> payload, Payment payment) {
        String amountRaw = first(payload, "pp_Amount");
        if (amountRaw == null || amountRaw.isBlank()) {
            if (properties.payments().jazzcash().sandbox() && "success".equalsIgnoreCase(payload.get("status"))) {
                return;
            }
            throw new BadRequestException("Missing JazzCash amount");
        }
        long paid;
        try {
            paid = Long.parseLong(amountRaw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid JazzCash amount");
        }
        if (paid != payment.getAmountPaisa()) {
            log.error("JazzCash amount mismatch paymentId={} expected={} got={}",
                    payment.getId(), payment.getAmountPaisa(), paid);
            throw new BadRequestException("JazzCash amount does not match order total");
        }
    }

    private void completePayment(Payment payment, Long actorId, String note) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setConfirmedAt(Instant.now());
        payment.setFailureReason(null);
        paymentRepository.save(payment);
        orderService.markPaymentConfirmed(payment.getOrderId(), actorId);
        log.info("Payment {} completed ({})", payment.getId(), note);
    }

    @Transactional
    public PaymentDtos.PaymentResponse simulateJazzCashSuccess(Long paymentId) {
        if (!properties.payments().jazzcash().sandbox()) {
            throw new BadRequestException("Simulation only allowed in sandbox");
        }
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        orderRepository.findByIdAndUserId(payment.getOrderId(), SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (payment.getMethod() != PaymentMethod.JAZZCASH) {
            throw new BadRequestException("Not a JazzCash payment");
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return toDto(payment);
        }
        if (payment.getExpiresAt() != null && payment.getExpiresAt().isBefore(Instant.now())) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setFailureReason("Payment link expired");
            paymentRepository.save(payment);
            throw new BadRequestException("JazzCash payment link expired");
        }
        payment.setRawPayload("{\"simulated\":true,\"sandbox\":true}");
        completePayment(payment, SecurityUtils.currentUserId(), "Sandbox simulate");
        return toDto(payment);
    }

    private PaymentDtos.PaymentResponse toDto(Payment payment) {
        ShopOrder order = orderRepository.findById(payment.getOrderId()).orElse(null);
        String publicCode = order != null ? order.getPublicCode() : null;

        PaymentDtos.BankInstructions bank = null;
        PaymentDtos.JazzCashRedirect jazz = null;

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER && order != null) {
            var b = properties.payments().bank();
            bank = new PaymentDtos.BankInstructions(
                    b.accountTitle(),
                    b.accountNumber(),
                    b.bankName(),
                    b.iban(),
                    order.getPublicCode(),
                    payment.getAmountPaisa(),
                    payment.getCurrency()
            );
        }

        boolean jazzPending = payment.getMethod() == PaymentMethod.JAZZCASH
                && payment.getStatus() == PaymentStatus.PENDING
                && (payment.getExpiresAt() == null || payment.getExpiresAt().isAfter(Instant.now()));
        if (jazzPending) {
            jazz = buildJazzCashRedirect(payment, order);
        }

        return new PaymentDtos.PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                publicCode,
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmountPaisa(),
                payment.getCurrency(),
                payment.getProviderRef(),
                payment.getProofUrl(),
                payment.getFailureReason(),
                payment.getExpiresAt(),
                payment.getConfirmedAt(),
                bank,
                jazz,
                payment.getCreatedAt()
        );
    }

    private PaymentDtos.JazzCashRedirect buildJazzCashRedirect(Payment payment, ShopOrder order) {
        var jc = properties.payments().jazzcash();
        if (order == null) {
            order = orderRepository.findById(payment.getOrderId()).orElseThrow();
        }
        String txnDateTime = LocalDateTime.now().format(JC_TS);
        Instant expires = payment.getExpiresAt() != null ? payment.getExpiresAt() : Instant.now().plusSeconds(3600);
        String txnExpiry = LocalDateTime.ofInstant(expires, java.time.ZoneOffset.UTC).format(JC_TS);
        String txnRef = payment.getProviderRef() != null ? payment.getProviderRef() : nextJazzCashTxnRef();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pp_Version", "1.1");
        fields.put("pp_TxnType", "MWALLET");
        fields.put("pp_Language", "EN");
        fields.put("pp_MerchantID", jc.merchantId());
        fields.put("pp_Password", jc.password());
        fields.put("pp_TxnRefNo", txnRef);
        fields.put("pp_Amount", String.valueOf(payment.getAmountPaisa()));
        fields.put("pp_TxnCurrency", "PKR");
        fields.put("pp_TxnDateTime", txnDateTime);
        fields.put("pp_BillReference", order.getPublicCode());
        fields.put("pp_Description", "NJAYS order " + order.getPublicCode());
        fields.put("pp_TxnExpiryDateTime", txnExpiry);
        fields.put("pp_ReturnURL", jc.returnUrl());
        fields.put("ppmpf_1", String.valueOf(payment.getId()));
        fields.put("ppmpf_2", order.getPublicCode());
        fields.put("pp_SecureHash", JazzCashCrypto.secureHash(fields, jc.integritySalt()));

        String actionUrl = jc.sandbox()
                ? "https://sandbox.jazzcash.com.pk/CustomerPortal/transactionmanagement/merchantform/"
                : "https://payments.jazzcash.com.pk/CustomerPortal/transactionmanagement/merchantform/";

        if (payment.getProviderRef() == null) {
            payment.setProviderRef(txnRef);
            paymentRepository.save(payment);
        }

        return new PaymentDtos.JazzCashRedirect(actionUrl, fields, jc.sandbox(), expires);
    }

    private static String nextJazzCashTxnRef() {
        String ts = LocalDateTime.now().format(JC_TS);
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "T" + ts + suffix;
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)
                        && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
