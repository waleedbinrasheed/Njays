package com.menswear.insights;

import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.insights.dto.InsightsDtos.Severity;
import com.menswear.insights.dto.InsightsDtos.Snapshot;
import com.menswear.orders.entity.OrderStatusHistory;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminInsightsServiceTest {

    // Fixed at noon UTC (~5pm Asia/Karachi) so day-boundary math never flakes near midnight.
    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final AdminInsightsService service = new AdminInsightsService(orderRepository, paymentRepository, clock);

    private static ShopOrder order(long id, OrderType type, OrderStatus status, Instant lastChangeAt) {
        ShopOrder order = ShopOrder.builder()
                .id(id)
                .publicCode("JH-2026-" + id)
                .userId(1L)
                .orderType(type)
                .status(status)
                .currency("PKR")
                .subtotalPaisa(500000L)
                .shippingPaisa(0L)
                .totalPaisa(500000L)
                .shippingAddressJson("{}")
                .whatsappPhone("+923001234567")
                .createdAt(lastChangeAt)
                .updatedAt(lastChangeAt)
                .statusHistory(new ArrayList<>())
                .build();
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .toStatus(status)
                .createdAt(lastChangeAt)
                .build());
        return order;
    }

    private static Payment payment(long id, long orderId, PaymentMethod method, PaymentStatus status,
                                    long amountPaisa, Instant createdAt, Instant confirmedAt, String proofUrl,
                                    Instant expiresAt) {
        return Payment.builder()
                .id(id)
                .orderId(orderId)
                .method(method)
                .status(status)
                .amountPaisa(amountPaisa)
                .currency("PKR")
                .idempotencyKey("key-" + id)
                .proofUrl(proofUrl)
                .expiresAt(expiresAt)
                .confirmedAt(confirmedAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    @Test
    void flagsOrdersStuckPastTheirStageThreshold() {
        Instant now = FIXED_NOW;
        ShopOrder freshCutting = order(1, OrderType.CUSTOM, OrderStatus.IN_CUTTING, now.minus(2, ChronoUnit.HOURS));
        ShopOrder staleCutting = order(2, OrderType.CUSTOM, OrderStatus.IN_CUTTING, now.minus(3, ChronoUnit.DAYS));
        ShopOrder deliveredLongAgo = order(3, OrderType.READY, OrderStatus.DELIVERED, now.minus(30, ChronoUnit.DAYS));

        when(orderRepository.findAll()).thenReturn(List.of(freshCutting, staleCutting, deliveredLongAgo));
        when(paymentRepository.findAll()).thenReturn(List.of());

        Snapshot snap = service.snapshot();

        assertThat(snap.ordersActive()).isEqualTo(2); // delivered order is terminal, excluded
        assertThat(snap.ordersStuckCount()).isEqualTo(1);
        assertThat(snap.stuckOrders().get(0).publicCode()).isEqualTo("JH-2026-2");
        assertThat(snap.highlights())
                .anySatisfy(h -> assertThat(h.category()).isEqualTo("ORDERS"));
    }

    @Test
    void flagsStaleBankTransferProofsAsCritical() {
        Instant now = FIXED_NOW;
        ShopOrder o = order(10, OrderType.CUSTOM, OrderStatus.PAYMENT_PENDING, now.minus(1, ChronoUnit.HOURS));
        Payment stalePendingBank = payment(1, 10, PaymentMethod.BANK_TRANSFER, PaymentStatus.AWAITING_CONFIRMATION,
                500000L, now.minus(8, ChronoUnit.HOURS), null, "https://proof.example/1.png", null);

        when(orderRepository.findAll()).thenReturn(List.of(o));
        when(paymentRepository.findAll()).thenReturn(List.of(stalePendingBank));

        Snapshot snap = service.snapshot();

        assertThat(snap.bankAwaitingConfirmationCount()).isEqualTo(1);
        assertThat(snap.paymentsPendingCount()).isEqualTo(1);
        assertThat(snap.pendingAmountPaisa()).isEqualTo(500000L);
        assertThat(snap.highlights())
                .anySatisfy(h -> assertThat(h.severity()).isEqualTo(Severity.CRITICAL));
    }

    @Test
    void computesRevenueWindowsFromCompletedPayments() {
        Instant now = FIXED_NOW;
        ShopOrder o = order(20, OrderType.READY, OrderStatus.PAYMENT_CONFIRMED, now.minus(1, ChronoUnit.HOURS));
        Payment paidToday = payment(2, 20, PaymentMethod.COD, PaymentStatus.COMPLETED,
                300000L, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), null, null);
        Payment paidLastWeek = payment(3, 20, PaymentMethod.COD, PaymentStatus.COMPLETED,
                200000L, now.minus(3, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS), null, null);
        Payment paidLastYear = payment(4, 20, PaymentMethod.COD, PaymentStatus.COMPLETED,
                900000L, now.minus(400, ChronoUnit.DAYS), now.minus(400, ChronoUnit.DAYS), null, null);

        when(orderRepository.findAll()).thenReturn(List.of(o));
        when(paymentRepository.findAll()).thenReturn(List.of(paidToday, paidLastWeek, paidLastYear));

        Snapshot snap = service.snapshot();

        assertThat(snap.revenueTodayPaisa()).isEqualTo(300000L);
        assertThat(snap.revenueWeekPaisa()).isEqualTo(500000L);
        assertThat(snap.revenueMonthPaisa()).isEqualTo(500000L);
    }
}
