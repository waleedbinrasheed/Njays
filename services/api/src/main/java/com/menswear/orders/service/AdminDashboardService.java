package com.menswear.orders.service;

import com.menswear.common.enums.OrderStatus;
import com.menswear.orders.dto.DashboardDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.payments.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Backs the admin dashboard's "Summary" counts — plain numbers only, no charts. */
@Service
public class AdminDashboardService {

    private static final Set<OrderStatus> IN_PROGRESS = EnumSet.of(
            OrderStatus.IN_CUTTING, OrderStatus.IN_STITCHING, OrderStatus.QUALITY_CHECK
    );
    private static final Set<OrderStatus> TERMINAL = EnumSet.of(
            OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.RETURNED
    );

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;

    public AdminDashboardService(OrderService orderService, PaymentRepository paymentRepository) {
        this.orderService = orderService;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.SummaryResponse summary() {
        List<OrderDtos.OrderResponse> orders = orderService.adminList();
        LocalDate today = LocalDate.now();
        Instant startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();

        long todaysOrders = orders.stream().filter(o -> !o.createdAt().isBefore(startOfToday)).count();
        long ordersInProgress = orders.stream().filter(o -> IN_PROGRESS.contains(o.status())).count();
        long readyForDelivery = orders.stream()
                .filter(o -> o.status() == OrderStatus.READY_TO_DISPATCH && sameBranch(o))
                .count();
        long awaitingDispatch = orders.stream()
                .filter(o -> o.status() == OrderStatus.READY_TO_DISPATCH && !sameBranch(o))
                .count();
        long dueToday = orders.stream()
                .filter(o -> o.expectedDeliveryDate() != null
                        && o.expectedDeliveryDate().isEqual(today)
                        && !TERMINAL.contains(o.status()))
                .count();
        long overdue = orders.stream()
                .filter(o -> o.expectedDeliveryDate() != null
                        && o.expectedDeliveryDate().isBefore(today)
                        && !TERMINAL.contains(o.status()))
                .count();
        long outstanding = orders.stream()
                .filter(o -> o.balanceDuePaisa() > 0 && o.status() != OrderStatus.CANCELLED)
                .count();
        long todaysPayments = paymentRepository.sumCompletedSince(startOfToday);

        return new DashboardDtos.SummaryResponse(
                todaysOrders, ordersInProgress, readyForDelivery, dueToday, overdue, todaysPayments, outstanding, awaitingDispatch
        );
    }

    private boolean sameBranch(OrderDtos.OrderResponse o) {
        return o.dispatchBranchId() == null || o.dispatchBranchId().equals(o.createdBranchId());
    }
}
