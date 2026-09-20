package com.menswear.orders.service;

import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.orders.dto.AdminOrderDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * In-shop point-of-sale order creation: admin places an order on behalf of a
 * walk-in customer. Unlike the online flow (where COD/bank/JazzCash payments
 * start PENDING and are confirmed later), an in-person sale is already
 * settled when the admin creates it, so this immediately records a COMPLETED
 * CASH payment and confirms the order in the same transaction.
 */
@Service
public class AdminOrderService {

    private final OrderService orderService;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public AdminOrderService(OrderService orderService, UserRepository userRepository, PaymentRepository paymentRepository) {
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public OrderDtos.OrderResponse createWalkInOrder(AdminOrderDtos.CreateOrderRequest request) {
        userRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found"));

        Long actorId = SecurityUtils.currentUserId();
        OrderDtos.OrderResponse created = orderService.createForCustomer(request.customerId(), request, actorId);

        Payment payment = Payment.builder()
                .orderId(created.id())
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amountPaisa(created.totalPaisa())
                .currency(created.currency())
                .idempotencyKey("walkin-" + created.id() + "-" + UUID.randomUUID())
                .confirmedAt(Instant.now())
                .build();
        paymentRepository.save(payment);
        orderService.markPaymentConfirmed(created.id(), actorId);

        return orderService.adminGet(created.id());
    }
}
