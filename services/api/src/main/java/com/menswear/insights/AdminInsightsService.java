package com.menswear.insights;

import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.insights.dto.InsightsDtos;
import com.menswear.insights.dto.InsightsDtos.Highlight;
import com.menswear.insights.dto.InsightsDtos.Severity;
import com.menswear.insights.dto.InsightsDtos.StuckOrder;
import com.menswear.orders.entity.OrderStatusHistory;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, rule-based read model over orders/payments for the admin "AI assistant" panel.
 * No external LLM call - everything here is computed from live data with hand-picked thresholds
 * so admins get instant, explainable answers instead of hallucination risk or an API dependency.
 */
@Service
public class AdminInsightsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Karachi");

    private static final Set<OrderStatus> TERMINAL =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.RETURNED);

    private static final Map<OrderStatus, Duration> STUCK_AFTER = Map.of(
            OrderStatus.MEASUREMENT_SUBMITTED, Duration.ofHours(24),
            OrderStatus.PAYMENT_PENDING, Duration.ofHours(24),
            OrderStatus.PAYMENT_CONFIRMED, Duration.ofHours(24),
            OrderStatus.IN_CUTTING, Duration.ofHours(48),
            OrderStatus.IN_STITCHING, Duration.ofHours(72),
            OrderStatus.QUALITY_CHECK, Duration.ofHours(24),
            OrderStatus.READY_TO_DISPATCH, Duration.ofHours(24)
    );
    private static final Duration DEFAULT_STUCK_AFTER = Duration.ofHours(48);
    private static final Duration BANK_PROOF_STALE_AFTER = Duration.ofHours(6);
    private static final Duration JAZZCASH_EXPIRING_SOON = Duration.ofMinutes(15);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public AdminInsightsService(OrderRepository orderRepository, PaymentRepository paymentRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InsightsDtos.Snapshot snapshot() {
        Instant now = clock.instant();
        List<ShopOrder> orders = orderRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        List<ShopOrder> active = orders.stream()
                .filter(o -> !TERMINAL.contains(o.getStatus()))
                .toList();

        record Candidate(ShopOrder order, Duration age) {}

        List<Candidate> stuckCandidates = active.stream()
                .map(o -> new Candidate(o, ageInStatus(o, now)))
                .filter(c -> c.age().compareTo(thresholdFor(c.order().getStatus())) > 0)
                .sorted(Comparator.comparing(Candidate::age).reversed())
                .toList();

        List<StuckOrder> stuckOrders = stuckCandidates.stream()
                .limit(6)
                .map(c -> new StuckOrder(c.order().getPublicCode(), c.order().getStatus().name(), humanDuration(c.age())))
                .toList();

        long customActive = active.stream().filter(o -> o.getOrderType() == OrderType.CUSTOM).count();
        long readyActive = active.size() - customActive;

        List<Payment> openPayments = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.AWAITING_CONFIRMATION)
                .toList();
        long pendingAmount = openPayments.stream().mapToLong(Payment::getAmountPaisa).sum();

        List<Payment> bankAwaiting = openPayments.stream()
                .filter(p -> p.getMethod() == PaymentMethod.BANK_TRANSFER)
                .toList();
        long bankAwaitingAmount = bankAwaiting.stream().mapToLong(Payment::getAmountPaisa).sum();

        long jazzOpen = openPayments.stream().filter(p -> p.getMethod() == PaymentMethod.JAZZCASH).count();
        long jazzExpiringSoon = openPayments.stream()
                .filter(p -> p.getMethod() == PaymentMethod.JAZZCASH && p.getExpiresAt() != null)
                .filter(p -> {
                    Duration remaining = Duration.between(now, p.getExpiresAt());
                    return remaining.compareTo(Duration.ZERO) > 0 && remaining.compareTo(JAZZCASH_EXPIRING_SOON) < 0;
                })
                .count();

        long codPending = openPayments.stream().filter(p -> p.getMethod() == PaymentMethod.COD).count();

        List<Payment> completed = payments.stream().filter(p -> p.getStatus() == PaymentStatus.COMPLETED).toList();
        ZonedDateTime startOfDay = now.atZone(ZONE).toLocalDate().atStartOfDay(ZONE);
        Instant todayStart = startOfDay.toInstant();
        Instant weekStart = now.minus(Duration.ofDays(7));
        Instant monthStart = now.minus(Duration.ofDays(30));

        long revenueToday = sumSince(completed, todayStart);
        long revenueTodayCount = countSince(completed, todayStart);
        long revenueWeek = sumSince(completed, weekStart);
        long revenueMonth = sumSince(completed, monthStart);

        List<Highlight> highlights = new ArrayList<>();

        long staleBankAwaitingProof = bankAwaiting.stream()
                .filter(p -> p.getProofUrl() != null && !p.getProofUrl().isBlank())
                .filter(p -> Duration.between(p.getCreatedAt(), now).compareTo(BANK_PROOF_STALE_AFTER) > 0)
                .count();
        if (staleBankAwaitingProof > 0) {
            highlights.add(new Highlight(Severity.CRITICAL, "PAYMENTS",
                    staleBankAwaitingProof + " bank transfer payment(s) ready for confirmation",
                    "Proof was uploaded over " + humanDuration(BANK_PROOF_STALE_AFTER)
                            + " ago and is still waiting on your review - confirm or reject it in Pending payments."));
        }

        if (!stuckOrders.isEmpty()) {
            StuckOrder oldest = stuckOrders.get(0);
            highlights.add(new Highlight(Severity.WARN, "ORDERS",
                    stuckOrders.size() + (stuckOrders.size() == 1 ? " order hasn't" : " orders haven't") + " moved stage in a while",
                    "Oldest: " + oldest.publicCode() + " has been in " + oldest.status() + " for " + oldest.age() + "."));
        }

        if (jazzExpiringSoon > 0) {
            highlights.add(new Highlight(Severity.WARN, "JAZZCASH",
                    jazzExpiringSoon + " JazzCash session(s) expiring soon",
                    "These payment links expire within 15 minutes if the customer doesn't complete them."));
        }

        if (!openPayments.isEmpty()) {
            highlights.add(new Highlight(Severity.INFO, "PAYMENTS",
                    openPayments.size() + " payment(s) awaiting completion or confirmation",
                    "Total value " + pkr(pendingAmount) + " across COD, bank transfer and JazzCash."));
        }

        highlights.add(new Highlight(Severity.INFO, "REVENUE",
                "Confirmed revenue today: " + pkr(revenueToday),
                "Last 7 days: " + pkr(revenueWeek) + "; last 30 days: " + pkr(revenueMonth) + "."));

        highlights.add(new Highlight(Severity.INFO, "ORDERS",
                active.size() + " active order(s) in the pipeline",
                customActive + " made-to-measure, " + readyActive + " ready-made."));

        String summary = buildSummary(active.size(), stuckOrders.size(), openPayments.size(), pendingAmount, revenueToday);

        return new InsightsDtos.Snapshot(
                summary,
                highlights,
                active.size(),
                stuckOrders.size(),
                stuckOrders,
                openPayments.size(),
                pendingAmount,
                bankAwaiting.size(),
                bankAwaitingAmount,
                jazzOpen,
                jazzExpiringSoon,
                codPending,
                revenueToday,
                revenueTodayCount,
                revenueWeek,
                revenueMonth,
                customActive,
                readyActive,
                now
        );
    }

    private Duration ageInStatus(ShopOrder order, Instant now) {
        List<OrderStatusHistory> history = order.getStatusHistory();
        Instant last = history.isEmpty() ? order.getCreatedAt() : history.get(history.size() - 1).getCreatedAt();
        return Duration.between(last, now);
    }

    private Duration thresholdFor(OrderStatus status) {
        return STUCK_AFTER.getOrDefault(status, DEFAULT_STUCK_AFTER);
    }

    private static long sumSince(List<Payment> payments, Instant since) {
        return payments.stream()
                .filter(p -> effectiveTime(p).isAfter(since))
                .mapToLong(Payment::getAmountPaisa)
                .sum();
    }

    private static long countSince(List<Payment> payments, Instant since) {
        return payments.stream().filter(p -> effectiveTime(p).isAfter(since)).count();
    }

    private static Instant effectiveTime(Payment p) {
        return p.getConfirmedAt() != null ? p.getConfirmedAt() : p.getCreatedAt();
    }

    static String humanDuration(Duration d) {
        long hours = d.toHours();
        if (hours < 1) {
            long minutes = Math.max(1, d.toMinutes());
            return minutes + "m";
        }
        if (hours < 48) {
            return hours + "h";
        }
        long days = d.toDays();
        long remHours = hours - days * 24;
        return remHours > 0 ? days + "d " + remHours + "h" : days + "d";
    }

    static String pkr(long paisa) {
        long rupees = Math.round(paisa / 100.0);
        return "PKR " + String.format(Locale.US, "%,d", rupees);
    }

    private String buildSummary(long active, long stuckCount, long openPaymentsCount, long pendingAmount, long revenueToday) {
        StringBuilder sb = new StringBuilder();
        sb.append("You have ").append(active).append(active == 1 ? " active order" : " active orders")
                .append(" in the pipeline. ");
        if (stuckCount > 0) {
            sb.append(stuckCount).append(stuckCount == 1 ? " order hasn't" : " orders haven't")
                    .append(" moved stage in longer than expected - worth a look. ");
        } else {
            sb.append("Everything is moving through stages on schedule. ");
        }
        if (openPaymentsCount > 0) {
            sb.append(openPaymentsCount).append(" payment(s) totalling ").append(pkr(pendingAmount))
                    .append(" are still open. ");
        } else {
            sb.append("No payments are currently pending. ");
        }
        sb.append("Confirmed revenue today: ").append(pkr(revenueToday)).append(".");
        return sb.toString();
    }
}
