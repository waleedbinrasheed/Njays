package com.menswear.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.enums.Role;
import com.menswear.common.exception.NotFoundException;
import com.menswear.config.MenswearProperties;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.measurements.entity.MeasurementProfile;
import com.menswear.orders.dto.InvoiceDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.orders.entity.OrderItem;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import com.menswear.identity.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MenswearProperties properties = new MenswearProperties(
            new MenswearProperties.Jwt("secret", 120, 14),
            new MenswearProperties.Cors(List.of("http://localhost:3000")),
            new MenswearProperties.Whatsapp("923001234567"),
            new MenswearProperties.Payments(
                    new MenswearProperties.Bank("Title", "Acct", "Bank", "IBAN"),
                    new MenswearProperties.Jazzcash("MID", "PW", "SALT", "return", "frontendReturn", true, true)
            ),
            new MenswearProperties.Frontend("http://localhost:3000", true),
            new MenswearProperties.Business("NJAY'S by S.A.R", "12 Tariq Road, Karachi", "+923001234567", "hi@njays.example"),
            "PKR"
    );

    private final InvoiceService service = new InvoiceService(
            orderRepository, userRepository, paymentRepository, properties, objectMapper
    );

    private User sampleUser() {
        return User.builder()
                .id(9L).email("buyer@example.com").passwordHash("hash")
                .fullName("Ayesha Khan").phone("923001112222")
                .role(Role.CUSTOMER).enabled(true).build();
    }

    private ShopOrder sampleOrder(String addressJson, List<OrderItem> items) {
        ShopOrder order = ShopOrder.builder()
                .id(5L)
                .publicCode("JH-2026-77777")
                .userId(9L)
                .orderType(OrderType.CUSTOM)
                .status(OrderStatus.IN_STITCHING)
                .currency("PKR")
                .subtotalPaisa(1000000L)
                .shippingPaisa(50000L)
                .totalPaisa(1050000L)
                .shippingAddressJson(addressJson)
                .whatsappPhone("923005556666")
                .createdAt(Instant.parse("2026-01-10T10:00:00Z"))
                .updatedAt(Instant.parse("2026-01-10T10:00:00Z"))
                .items(new ArrayList<>(items))
                .build();
        items.forEach(i -> i.setOrder(order));
        return order;
    }

    private String measurementJson(MeasurementProfile profile) throws Exception {
        return objectMapper.writeValueAsString(profile);
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanFetchAnyOrderInvoiceWithoutOwnershipCheck() {
        ShopOrder order = sampleOrder(null, List.of());
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.business().name()).isEqualTo("NJAY'S by S.A.R");
        assertThat(response.customer().fullName()).isEqualTo("Ayesha Khan");
        assertThat(response.order().orderNumber()).isEqualTo("JH-2026-77777");
    }

    @Test
    void adminInvoiceThrowsWhenOrderMissing() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forAdmin(404L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void customerCanFetchOwnOrderInvoice() {
        authenticateAs(sampleUser()); // id = 9L, matches sampleOrder's userId
        ShopOrder order = sampleOrder(null, List.of());
        when(orderRepository.findByIdAndUserId(5L, 9L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        InvoiceDtos.InvoiceResponse response = service.forCustomer(5L);

        assertThat(response.order().orderNumber()).isEqualTo("JH-2026-77777");
    }

    @Test
    void customerCannotFetchAnotherCustomersOrderInvoice() {
        User someoneElse = User.builder()
                .id(99L).email("other@example.com").passwordHash("hash")
                .fullName("Other Person").role(Role.CUSTOMER).enabled(true).build();
        authenticateAs(someoneElse);
        // findByIdAndUserId(5L, 99L) is never stubbed -> Mockito default returns Optional.empty()

        assertThatThrownBy(() -> service.forCustomer(5L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void computesAmountPaidAndBalanceDueFromCompletedPaymentsOnly() {
        ShopOrder order = sampleOrder(null, List.of());
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));

        Payment completed = Payment.builder().id(1L).orderId(5L).method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.COMPLETED).amountPaisa(700000L).currency("PKR")
                .idempotencyKey("k1").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Payment cancelled = Payment.builder().id(2L).orderId(5L).method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.CANCELLED).amountPaisa(1050000L).currency("PKR")
                .idempotencyKey("k2").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(completed, cancelled));

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.totals().amountPaidPaisa()).isEqualTo(700000L);
        assertThat(response.totals().balanceDuePaisa()).isEqualTo(350000L); // 1,050,000 - 700,000
        assertThat(response.totals().totalPaisa()).isEqualTo(1050000L);
        assertThat(response.totals().subtotalPaisa()).isEqualTo(1000000L);
        assertThat(response.totals().shippingPaisa()).isEqualTo(50000L);
    }

    @Test
    void balanceDueNeverGoesNegativeOnOverpayment() {
        ShopOrder order = sampleOrder(null, List.of());
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));

        Payment overpaid = Payment.builder().id(1L).orderId(5L).method(PaymentMethod.JAZZCASH)
                .status(PaymentStatus.COMPLETED).amountPaisa(2000000L).currency("PKR")
                .idempotencyKey("k1").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(overpaid));

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.totals().balanceDuePaisa()).isZero();
    }

    @Test
    void parsesShippingAddressFromStoredJson() throws Exception {
        var address = new OrderDtos.AddressDto("House 4", "Street 9", "Lahore", "Punjab", "54000", "PK");
        String addressJson = objectMapper.writeValueAsString(address);
        ShopOrder order = sampleOrder(addressJson, List.of());
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.customer().shippingAddress()).isNotNull();
        assertThat(response.customer().shippingAddress().city()).isEqualTo("Lahore");
        assertThat(response.customer().shippingAddress().line1()).isEqualTo("House 4");
    }

    @Test
    void malformedAddressJsonIsHandledGracefullyNotThrown() {
        ShopOrder order = sampleOrder("{not valid json", List.of());
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.customer().shippingAddress()).isNull();
    }

    @Test
    void parsesPerItemMeasurementSnapshotFromStoredJson() throws Exception {
        MeasurementProfile profile = MeasurementProfile.builder()
                .id(3L).userId(9L).name("My custom fit").unit("INCH")
                .kameezLength(new BigDecimal("42.5")).chest(new BigDecimal("40"))
                .waist(new BigDecimal("36")).shoulder(new BigDecimal("18"))
                .backStyle("BOX").collarStyle("MANDARIN")
                .isDefault(true).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        OrderItem item = OrderItem.builder()
                .id(1L).productId(2L).productName("Elegant Black Kameez Shalwar")
                .quantity(1).custom(true).fabricLabel("Premium / Navy (NV1)")
                .measurementJson(measurementJson(profile))
                .unitPricePaisa(850000L).lineTotalPaisa(850000L)
                .build();
        ShopOrder order = sampleOrder(null, List.of(item));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.items()).hasSize(1);
        InvoiceDtos.MeasurementSnapshot measurements = response.items().get(0).measurements();
        assertThat(measurements).isNotNull();
        assertThat(measurements.name()).isEqualTo("My custom fit");
        assertThat(measurements.kameezLength()).isEqualByComparingTo("42.5");
        assertThat(measurements.backStyle()).isEqualTo("BOX");
    }

    @Test
    void readyMadeItemHasNoMeasurements() {
        OrderItem item = OrderItem.builder()
                .id(1L).productId(2L).productName("Classic Waistcoat")
                .quantity(1).custom(false)
                .measurementJson(null)
                .unitPricePaisa(450000L).lineTotalPaisa(450000L)
                .build();
        ShopOrder order = sampleOrder(null, List.of(item));
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userRepository.findById(9L)).thenReturn(Optional.of(sampleUser()));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        InvoiceDtos.InvoiceResponse response = service.forAdmin(5L);

        assertThat(response.items().get(0).measurements()).isNull();
    }
}
