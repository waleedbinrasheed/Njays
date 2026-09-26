package com.menswear.orders.service;

import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.enums.Role;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.UserPrincipal;
import com.menswear.orders.dto.AdminOrderDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderServiceTest {

    private final OrderService orderService = mock(OrderService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);

    private final AdminOrderService service = new AdminOrderService(orderService, userRepository, paymentRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        User admin = User.builder().id(1L).email("admin@menswear.local").passwordHash("hash")
                .fullName("Admin").role(Role.ADMIN).enabled(true).build();
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private AdminOrderDtos.CreateOrderRequest sampleRequest() {
        var address = new OrderDtos.AddressDto("Shop counter", null, "Karachi", "Sindh", "75500", "PK");
        var item = new AdminOrderDtos.CreateOrderItemRequest(1L, 1, false, null, null);
        return new AdminOrderDtos.CreateOrderRequest(9L, address, "03001234567", null, List.of(item));
    }

    private OrderDtos.OrderResponse sampleOrderResponse(OrderStatus status) {
        return new OrderDtos.OrderResponse(
                50L, "JH-2026-99999", OrderType.READY, status, "PKR",
                150000L, 0L, 150000L, "923001234567", null,
                List.of(), List.of(), Instant.now()
        );
    }

    @Test
    void rejectsUnknownCustomer() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWalkInOrder(sampleRequest()))
                .isInstanceOf(NotFoundException.class);

        verify(orderService, never()).createForCustomer(any(), any(), any());
    }

    @Test
    void createsOrderThenImmediatelyRecordsCompletedCashPaymentAndConfirms() {
        authenticateAsAdmin();
        User customer = User.builder().id(9L).fullName("Ayesha").phone("923001112222").role(Role.CUSTOMER).enabled(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));
        when(orderService.createForCustomer(eq(9L), any(), eq(1L))).thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING));
        when(orderService.adminGet(50L)).thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_CONFIRMED));

        OrderDtos.OrderResponse result = service.createWalkInOrder(sampleRequest());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.getAmountPaisa()).isEqualTo(150000L);
        assertThat(saved.getOrderId()).isEqualTo(50L);
        assertThat(saved.getConfirmedAt()).isNotNull();

        verify(orderService).markPaymentConfirmed(50L, 1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }
}
