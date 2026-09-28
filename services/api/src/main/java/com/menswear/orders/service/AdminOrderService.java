package com.menswear.orders.service;

import com.menswear.branch.repo.BranchRepository;
import com.menswear.branch.service.DispatchCostService;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.entity.User;
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
 * start PENDING and are confirmed later), an in-person sale is settled at
 * least partially at the point of creation, so this immediately records the
 * given initial payment (which may be less than the total — a deposit).
 */
@Service
public class AdminOrderService {

    private final OrderService orderService;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BranchRepository branchRepository;
    private final DispatchCostService dispatchCostService;

    public AdminOrderService(
            OrderService orderService,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            BranchRepository branchRepository,
            DispatchCostService dispatchCostService
    ) {
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.branchRepository = branchRepository;
        this.dispatchCostService = dispatchCostService;
    }

    @Transactional
    public OrderDtos.OrderResponse createWalkInOrder(AdminOrderDtos.CreateOrderRequest request) {
        userRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found"));
        branchRepository.findById(request.dispatchBranchId())
                .orElseThrow(() -> new NotFoundException("Dispatch branch not found"));

        Long actorId = SecurityUtils.currentUserId();
        Long createdBranchId = resolveCreatedBranchId(request.createdBranchId(), actorId);
        long dispatchCostPaisa = dispatchCostService.resolve(createdBranchId, request.dispatchBranchId());

        OrderDtos.OrderResponse created = orderService.createForCustomer(
                request.customerId(), request, actorId, createdBranchId, dispatchCostPaisa
        );

        long paymentAmount = request.payment().amountPaisa();
        if (paymentAmount > created.totalPaisa()) {
            throw new BadRequestException("Payment cannot exceed the order total");
        }
        if (paymentAmount > 0) {
            recordSettledPayment(created.id(), request.payment().method(), paymentAmount, actorId);
        }

        return orderService.adminGet(created.id());
    }

    /** Records an additional payment (e.g. collecting the remaining balance later) against an existing order. */
    @Transactional
    public OrderDtos.OrderResponse recordPayment(Long orderId, AdminOrderDtos.RecordPaymentRequest request) {
        OrderDtos.OrderResponse order = orderService.adminGet(orderId);
        if (request.amountPaisa() > order.balanceDuePaisa()) {
            throw new BadRequestException("Payment (" + request.amountPaisa() + ") exceeds the remaining balance (" + order.balanceDuePaisa() + ")");
        }
        recordSettledPayment(orderId, request.method(), request.amountPaisa(), SecurityUtils.currentUserId());
        return orderService.adminGet(orderId);
    }

    private void recordSettledPayment(Long orderId, PaymentMethod method, long amountPaisa, Long actorId) {
        OrderDtos.OrderResponse order = orderService.adminGet(orderId);
        Payment payment = Payment.builder()
                .orderId(orderId)
                .method(method)
                .status(PaymentStatus.COMPLETED)
                .amountPaisa(amountPaisa)
                .currency(order.currency())
                .idempotencyKey("admin-" + orderId + "-" + UUID.randomUUID())
                .confirmedAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        long amountPaid = order.amountPaidPaisa() + amountPaisa;
        if (amountPaid >= order.totalPaisa()) {
            orderService.markPaymentConfirmed(orderId, actorId);
        }
    }

    private Long resolveCreatedBranchId(Long requested, Long actorId) {
        if (requested != null) {
            branchRepository.findById(requested).orElseThrow(() -> new NotFoundException("Created-at branch not found"));
            return requested;
        }
        User actor = userRepository.findById(actorId).orElse(null);
        return actor != null ? actor.getBranchId() : null;
    }
}
