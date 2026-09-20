package com.menswear.legacyimport;

import com.menswear.catalog.entity.Product;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.enums.Role;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyOrderImportWorkerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final LegacyOrderImportWorker worker = new LegacyOrderImportWorker(
            userRepository, orderRepository, paymentRepository, productRepository, passwordEncoder
    );

    private static final Long LEGACY_PRODUCT_ID = 999L;
    private static final String LEGACY_PRODUCT_NAME = "Custom Stitched Suit (Legacy)";

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            ShopOrder o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(300L);
            return u;
        });
    }

    /**
     * phone must already be in AuthService.normalizePhone's canonical output shape
     * (92XXXXXXXXXX) by the time it reaches the worker. The raw ledger value is a bare
     * 10-digit number with no leading 0 (e.g. "3350390242") — AuthService.normalizePhone
     * only rewrites numbers starting with "0", so it would NOT fix that on its own (see
     * javaNormalizePhoneDoesNotFixBareTenDigitNumbers below). The Python parser that
     * builds the real import payload is responsible for producing the canonical form.
     */
    private LegacyImportDtos.OrderRequest sampleRequest(String legacyRef, String phone, Long totalPaisa, Long paidPaisa, String status) {
        var item = new LegacyImportDtos.ItemRequest("Grace", 5.0, 110000L, 550000L, 300000L);
        return new LegacyImportDtos.OrderRequest(
                legacyRef, "Sabir Ali", phone, "2", 1, "Turbat branch",
                "2026-04-26", totalPaisa, paidPaisa, status, List.of(item)
        );
    }

    @Test
    void javaNormalizePhoneDoesNotFixBareTenDigitNumbers() {
        // Regression guard: if this ever starts returning "923350390242", the Python
        // parser's pre-normalization step could be simplified away. Until then, the
        // import payload MUST send already-normalized phones, not raw ledger values.
        assertThat(com.menswear.identity.service.AuthService.normalizePhone("3350390242")).isEqualTo("3350390242");
    }

    @Test
    void skipsDuplicateLegacyRef() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(true);

        var result = worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        assertThat(result.outcome()).isEqualTo(LegacyOrderImportWorker.Outcome.SKIPPED_DUPLICATE);
        verify(userRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createsCustomerWithLocalFormatPhoneAsPassword() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone("923350390242")).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("923350390242");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        verify(passwordEncoder).encode("03350390242");
    }

    @Test
    void matchesExistingCustomerByPhoneInsteadOfCreating() {
        User existing = User.builder().id(7L).fullName("Sabir Ali").phone("923350390242").role(Role.CUSTOMER).enabled(true).build();
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone("923350390242")).thenReturn(Optional.of(existing));

        var result = worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        assertThat(result.customerCreated()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void unusablePhoneGetsOwnCustomerRecordNoLogin() {
        when(orderRepository.existsByLegacyRef("slip-x")).thenReturn(false);

        var result = worker.importOne(sampleRequest("slip-x", "0", 300000L, 300000L, null), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        assertThat(result.customerCreated()).isTrue();
        assertThat(result.warning()).contains("not a usable mobile number");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isNull();
        verify(userRepository, never()).findByPhone(any());
    }

    @Test
    void deliveredStatusMapsToDeliveredOrderStatus() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void correctionStatusMapsToInStitching() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 0L, "Correction"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.IN_STITCHING);
    }

    @Test
    void blankStatusWithPaymentMapsToPaymentConfirmed() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, null), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void blankStatusWithNoPaymentStaysPaymentPending() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 0L, null), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createsCompletedCashPaymentWhenPaidPositive() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(captor.getValue().getAmountPaisa()).isEqualTo(1700000L);
        verify(paymentRepository).backdate(eq(200L), any());
    }

    @Test
    void orderTotalUsesLedgerTotalNotSumOfItems() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());
        // item total is 550000+300000=850000, but ledger says total is 900000 (a reconciliation gap)
        worker.importOne(sampleRequest("slip-2", "923350390242", 900000L, 900000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalPaisa()).isEqualTo(900000L);
        assertThat(captor.getValue().getSubtotalPaisa()).isEqualTo(850000L);
    }

    @Test
    void backdatesOrderToHistoricalDate() {
        when(orderRepository.existsByLegacyRef("slip-2")).thenReturn(false);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        worker.importOne(sampleRequest("slip-2", "923350390242", 1700000L, 1700000L, "Delivered"), LEGACY_PRODUCT_ID, LEGACY_PRODUCT_NAME);

        verify(orderRepository).backdate(eq(100L), any());
    }
}
