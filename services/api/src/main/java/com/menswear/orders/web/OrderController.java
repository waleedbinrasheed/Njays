package com.menswear.orders.web;

import com.menswear.identity.security.SecurityUtils;
import com.menswear.orders.dto.AdminOrderDtos;
import com.menswear.orders.dto.DashboardDtos;
import com.menswear.orders.dto.InvoiceDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.orders.service.AdminDashboardService;
import com.menswear.orders.service.AdminOrderService;
import com.menswear.orders.service.InvoiceService;
import com.menswear.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final InvoiceService invoiceService;
    private final AdminOrderService adminOrderService;
    private final AdminDashboardService adminDashboardService;

    public OrderController(
            OrderService orderService,
            InvoiceService invoiceService,
            AdminOrderService adminOrderService,
            AdminDashboardService adminDashboardService
    ) {
        this.orderService = orderService;
        this.invoiceService = invoiceService;
        this.adminOrderService = adminOrderService;
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/admin/dashboard/summary")
    public DashboardDtos.SummaryResponse dashboardSummary() {
        return adminDashboardService.summary();
    }

    @PostMapping("/orders")
    public OrderDtos.OrderResponse create(@Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        return orderService.checkout(request);
    }

    @GetMapping("/orders")
    public List<OrderDtos.OrderResponse> mine() {
        return orderService.myOrders();
    }

    @GetMapping("/orders/{id}")
    public OrderDtos.OrderResponse one(@PathVariable Long id) {
        return orderService.myOrder(id);
    }

    @GetMapping("/orders/{id}/invoice")
    public InvoiceDtos.InvoiceResponse invoice(@PathVariable Long id) {
        return invoiceService.forCustomer(id);
    }

    @GetMapping("/admin/orders/{id}/invoice")
    public InvoiceDtos.InvoiceResponse adminInvoice(@PathVariable Long id) {
        return invoiceService.forAdmin(id);
    }

    @GetMapping("/track")
    public OrderDtos.TrackResponse track(
            @RequestParam String orderId,
            @RequestParam String phone
    ) {
        return orderService.track(orderId, phone);
    }

    @GetMapping("/admin/orders")
    public List<OrderDtos.OrderResponse> adminList() {
        return orderService.adminList();
    }

    @PostMapping("/admin/orders")
    public OrderDtos.OrderResponse createWalkInOrder(@Valid @RequestBody AdminOrderDtos.CreateOrderRequest request) {
        return adminOrderService.createWalkInOrder(request);
    }

    @PatchMapping("/admin/orders/{id}/status")
    public OrderDtos.OrderResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderDtos.UpdateStatusRequest request
    ) {
        return orderService.updateStatus(id, request, SecurityUtils.currentUserId());
    }

    @PostMapping("/admin/orders/{id}/payments")
    public OrderDtos.OrderResponse recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody AdminOrderDtos.RecordPaymentRequest request
    ) {
        return adminOrderService.recordPayment(id, request);
    }

    @GetMapping("/admin/customers/{id}/orders")
    public List<OrderDtos.OrderResponse> customerOrders(@PathVariable Long id) {
        return orderService.adminListForCustomer(id);
    }
}
