package com.menswear.orders.service;

import com.menswear.branch.entity.Branch;
import com.menswear.branch.repo.BranchRepository;
import com.menswear.branch.service.DispatchCostService;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.enums.Role;
import com.menswear.common.exception.BadRequestException;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderServiceTest {

    private static final Long DISPATCH_BRANCH_ID = 2L;

    private final OrderService orderService = mock(OrderService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final DispatchCostService dispatchCostService = mock(DispatchCostService.class);

    private final AdminOrderService service = new AdminOrderService(
            orderService, userRepository, paymentRepository, branchRepository, dispatchCostService
    );

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

    private AdminOrderDtos.CreateOrderRequest sampleRequest(long paymentAmountPaisa) {
        var address = new OrderDtos.AddressDto("Shop counter", null, "Karachi", "Sindh", "75500", "PK");
        var item = new AdminOrderDtos.CreateOrderItemRequest(1L, 1, false, null, null);
        var payment = new AdminOrderDtos.InitialPaymentRequest(PaymentMethod.CASH, paymentAmountPaisa);
        return new AdminOrderDtos.CreateOrderRequest(
                9L, address, "03001234567", null,
                null, DISPATCH_BRANCH_ID, 0L, null, payment, List.of(item)
        );
    }

    private OrderDtos.OrderResponse sampleOrderResponse(OrderStatus status, long totalPaisa, long amountPaidPaisa) {
        return new OrderDtos.OrderResponse(
                50L, "JH-2026-99999", OrderType.READY, status, "PKR",
                totalPaisa, 0L, 0L, totalPaisa, amountPaidPaisa, Math.max(0, totalPaisa - amountPaidPaisa),
                "923001234567", null,
                null, null, DISPATCH_BRANCH_ID, "DHA Branch", null,
                List.of(), List.of(), Instant.now()
        );
    }

    @Test
    void rejectsUnknownCustomer() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWalkInOrder(sampleRequest(150000L)))
                .isInstanceOf(NotFoundException.class);

        verify(orderService, never()).createForCustomer(any(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsUnknownDispatchBranch() {
        User customer = User.builder().id(9L).fullName("Ayesha").phone("923001112222").role(Role.CUSTOMER).enabled(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));
        when(branchRepository.findById(DISPATCH_BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createWalkInOrder(sampleRequest(150000L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsOrderThenRecordsFullCashPaymentAndConfirms() {
        authenticateAsAdmin();
        User customer = User.builder().id(9L).fullName("Ayesha").phone("923001112222").role(Role.CUSTOMER).enabled(true).build();
        Branch dispatchBranch = Branch.builder().id(DISPATCH_BRANCH_ID).name("DHA Branch").active(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));
        when(branchRepository.findById(DISPATCH_BRANCH_ID)).thenReturn(Optional.of(dispatchBranch));
        when(dispatchCostService.resolve(any(), eq(DISPATCH_BRANCH_ID))).thenReturn(0L);
        when(orderService.createForCustomer(eq(9L), any(), eq(1L), any(), eq(0L)))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 0L));
        when(orderService.adminGet(50L))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 0L))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_CONFIRMED, 150000L, 150000L));

        OrderDtos.OrderResponse result = service.createWalkInOrder(sampleRequest(150000L));

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

    @Test
    void partialPaymentLeavesOrderPendingWithoutConfirming() {
        authenticateAsAdmin();
        User customer = User.builder().id(9L).fullName("Ayesha").phone("923001112222").role(Role.CUSTOMER).enabled(true).build();
        Branch dispatchBranch = Branch.builder().id(DISPATCH_BRANCH_ID).name("DHA Branch").active(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));
        when(branchRepository.findById(DISPATCH_BRANCH_ID)).thenReturn(Optional.of(dispatchBranch));
        when(dispatchCostService.resolve(any(), eq(DISPATCH_BRANCH_ID))).thenReturn(0L);
        when(orderService.createForCustomer(eq(9L), any(), eq(1L), any(), eq(0L)))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 0L));
        when(orderService.adminGet(50L)).thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 0L));

        service.createWalkInOrder(sampleRequest(50000L));

        verify(paymentRepository).save(any());
        verify(orderService, never()).markPaymentConfirmed(any(), any());
    }

    @Test
    void rejectsPaymentExceedingOrderTotal() {
        authenticateAsAdmin();
        User customer = User.builder().id(9L).fullName("Ayesha").phone("923001112222").role(Role.CUSTOMER).enabled(true).build();
        Branch dispatchBranch = Branch.builder().id(DISPATCH_BRANCH_ID).name("DHA Branch").active(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));
        when(branchRepository.findById(DISPATCH_BRANCH_ID)).thenReturn(Optional.of(dispatchBranch));
        when(dispatchCostService.resolve(any(), eq(DISPATCH_BRANCH_ID))).thenReturn(0L);
        when(orderService.createForCustomer(eq(9L), any(), eq(1L), any(), eq(0L)))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 0L));

        assertThatThrownBy(() -> service.createWalkInOrder(sampleRequest(200000L)))
                .isInstanceOf(BadRequestException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPaymentRejectsAmountAboveRemainingBalance() {
        when(orderService.adminGet(50L)).thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 50000L));

        var request = new AdminOrderDtos.RecordPaymentRequest(PaymentMethod.CASH, 999999L);

        assertThatThrownBy(() -> service.recordPayment(50L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void recordPaymentConfirmsOrderOnceBalanceIsSettled() {
        when(orderService.adminGet(50L))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 50000L))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_PENDING, 150000L, 50000L))
                .thenReturn(sampleOrderResponse(OrderStatus.PAYMENT_CONFIRMED, 150000L, 150000L));

        var request = new AdminOrderDtos.RecordPaymentRequest(PaymentMethod.CASH, 100000L);
        OrderDtos.OrderResponse result = service.recordPayment(50L, request);

        verify(orderService).markPaymentConfirmed(eq(50L), any());
        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }
}
