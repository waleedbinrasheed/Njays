package com.menswear.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menswear.branch.entity.Branch;
import com.menswear.branch.repo.BranchRepository;
import com.menswear.cart.entity.Cart;
import com.menswear.cart.entity.CartItem;
import com.menswear.cart.service.CartService;
import com.menswear.catalog.entity.FabricColor;
import com.menswear.catalog.entity.Product;
import com.menswear.catalog.repo.FabricColorRepository;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.service.AuthService;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.measurements.entity.MeasurementProfile;
import com.menswear.measurements.repo.MeasurementProfileRepository;
import com.menswear.orders.dto.AdminOrderDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.orders.entity.OrderItem;
import com.menswear.orders.entity.OrderStatusHistory;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Set<OrderStatus> STAFF_ONLY = EnumSet.of(
            OrderStatus.IN_CUTTING,
            OrderStatus.IN_STITCHING,
            OrderStatus.QUALITY_CHECK,
            OrderStatus.READY_TO_DISPATCH,
            OrderStatus.DISPATCHED,
            OrderStatus.DELIVERED,
            OrderStatus.RETURNED
    );

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final FabricColorRepository fabricColorRepository;
    private final MeasurementProfileRepository measurementProfileRepository;
    private final BranchRepository branchRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            CartService cartService,
            ProductRepository productRepository,
            FabricColorRepository fabricColorRepository,
            MeasurementProfileRepository measurementProfileRepository,
            BranchRepository branchRepository,
            PaymentRepository paymentRepository,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.fabricColorRepository = fabricColorRepository;
        this.measurementProfileRepository = measurementProfileRepository;
        this.branchRepository = branchRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderDtos.OrderResponse checkout(OrderDtos.CreateOrderRequest request) {
        Cart cart = cartService.getCartEntity();
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        boolean anyCustom = cart.getItems().stream().anyMatch(CartItem::isCustom);
        OrderType type = anyCustom ? OrderType.CUSTOM : OrderType.READY;
        OrderStatus initial = anyCustom ? OrderStatus.MEASUREMENT_SUBMITTED : OrderStatus.PAYMENT_PENDING;

        long subtotal = 0;
        ShopOrder order = ShopOrder.builder()
                .publicCode(generateCode())
                .userId(SecurityUtils.currentUserId())
                .orderType(type)
                .status(initial)
                .currency("PKR")
                .discountPaisa(0L)
                .dispatchCostPaisa(0L)
                .shippingAddressJson(writeJson(request.shippingAddress()))
                .whatsappPhone(AuthService.normalizePhone(request.whatsappPhone()))
                .customerNote(request.customerNote())
                .build();

        for (CartItem ci : cart.getItems()) {
            Product product = productRepository.findById(ci.getProductId())
                    .orElseThrow(() -> new NotFoundException("Product missing"));
            String fabricLabel = null;
            String measurementJson = null;
            if (ci.isCustom()) {
                FabricColor color = fabricColorRepository.findById(ci.getFabricColorId()).orElseThrow();
                fabricLabel = color.getFabricTier().getName() + " / " + color.getName() + " (" + color.getCode() + ")";
                MeasurementProfile profile = measurementProfileRepository
                        .findByIdAndUserId(ci.getMeasurementProfileId(), SecurityUtils.currentUserId())
                        .orElseThrow();
                measurementJson = writeJson(profile);
            }
            long line = ci.getUnitPricePaisa() * ci.getQuantity();
            subtotal += line;
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(ci.getQuantity())
                    .custom(ci.isCustom())
                    .fabricColorId(ci.getFabricColorId())
                    .fabricLabel(fabricLabel)
                    .measurementJson(measurementJson)
                    .unitPricePaisa(ci.getUnitPricePaisa())
                    .lineTotalPaisa(line)
                    .build();
            order.getItems().add(item);
        }

        order.setSubtotalPaisa(subtotal);
        order.setTotalPaisa(subtotal);
        appendHistory(order, null, initial, "Order created", SecurityUtils.currentUserId());
        if (anyCustom) {
            appendHistory(order, initial, OrderStatus.PAYMENT_PENDING, "Awaiting payment", SecurityUtils.currentUserId());
            order.setStatus(OrderStatus.PAYMENT_PENDING);
        }

        ShopOrder saved = orderRepository.save(order);
        cart.getItems().clear();
        return toDto(saved, null);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> myOrders() {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtils.currentUserId())
                .stream().map(o -> toDto(o, null)).toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse myOrder(Long id) {
        return toDto(orderRepository.findByIdAndUserId(id, SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Order not found")), null);
    }

    @Transactional(readOnly = true)
    public OrderDtos.TrackResponse track(String publicCode, String phone) {
        String normalized = AuthService.normalizePhone(phone);
        ShopOrder order = orderRepository.findByPublicCodeAndWhatsappPhone(publicCode.trim().toUpperCase(), normalized)
                .or(() -> orderRepository.findByPublicCode(publicCode.trim().toUpperCase())
                        .filter(o -> normalized != null && normalized.equals(o.getWhatsappPhone())))
                .orElseThrow(() -> new NotFoundException("No order found for that code and phone"));
        return new OrderDtos.TrackResponse(
                order.getPublicCode(),
                order.getStatus(),
                order.getStatusHistory().stream().map(this::toHistory).toList()
        );
    }

    @Transactional
    public OrderDtos.OrderResponse updateStatus(Long orderId, OrderDtos.UpdateStatusRequest request, Long actorId) {
        ShopOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();
        if (from == to) {
            return toDto(order, null);
        }
        if (STAFF_ONLY.contains(to) && actorId == null) {
            throw new BadRequestException("Staff required for this status");
        }
        order.setStatus(to);
        appendHistory(order, from, to, request.note(), actorId);
        return toDto(orderRepository.save(order), null);
    }

    @Transactional
    public void markPaymentConfirmed(Long orderId, Long actorId) {
        ShopOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.PAYMENT_CONFIRMED) {
            return;
        }
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        appendHistory(order, from, OrderStatus.PAYMENT_CONFIRMED, "Payment confirmed", actorId);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> adminList() {
        Map<Long, String> branchNames = branchNameCache();
        return orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(o -> toDto(o, branchNames))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> adminListForCustomer(Long customerId) {
        Map<Long, String> branchNames = branchNameCache();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(customerId).stream()
                .map(o -> toDto(o, branchNames))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse adminGet(Long id) {
        return toDto(orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found")), null);
    }

    /**
     * In-shop point-of-sale order creation. Bypasses the cart entirely — items
     * are supplied directly — since the admin is acting on behalf of a walk-in
     * customer, not the currently authenticated user. Caller is responsible
     * for having already verified customerId refers to a real customer, and
     * for resolving createdBranchId/dispatchCostPaisa (see AdminOrderService).
     */
    @Transactional
    public OrderDtos.OrderResponse createForCustomer(
            Long customerId,
            AdminOrderDtos.CreateOrderRequest request,
            Long actorId,
            Long resolvedCreatedBranchId,
            long resolvedDispatchCostPaisa
    ) {
        boolean anyCustom = request.items().stream().anyMatch(AdminOrderDtos.CreateOrderItemRequest::custom);
        OrderType type = anyCustom ? OrderType.CUSTOM : OrderType.READY;
        OrderStatus initial = anyCustom ? OrderStatus.MEASUREMENT_SUBMITTED : OrderStatus.PAYMENT_PENDING;

        long discount = request.discountPaisa() == null ? 0L : request.discountPaisa();

        long subtotal = 0;
        ShopOrder order = ShopOrder.builder()
                .publicCode(generateCode())
                .userId(customerId)
                .orderType(type)
                .status(initial)
                .currency("PKR")
                .discountPaisa(discount)
                .dispatchCostPaisa(resolvedDispatchCostPaisa)
                .shippingAddressJson(writeJson(request.shippingAddress()))
                .whatsappPhone(AuthService.normalizePhone(request.whatsappPhone()))
                .customerNote(request.customerNote())
                .createdBranchId(resolvedCreatedBranchId)
                .dispatchBranchId(request.dispatchBranchId())
                .expectedDeliveryDate(request.expectedDeliveryDate())
                .build();

        for (AdminOrderDtos.CreateOrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

            long unitPrice = product.getBasePricePaisa();
            String fabricLabel = null;
            String measurementJson = null;
            if (itemReq.custom()) {
                if (!product.isSupportsCustom()) {
                    throw new BadRequestException("Product does not support custom measure: " + product.getName());
                }
                if (itemReq.fabricColorId() == null || itemReq.measurementProfileId() == null) {
                    throw new BadRequestException("Custom items require a fabric color and a measurement profile");
                }
                FabricColor color = fabricColorRepository.findById(itemReq.fabricColorId())
                        .orElseThrow(() -> new NotFoundException("Fabric color not found"));
                unitPrice += color.getFabricTier().getSurchargePaisa();
                fabricLabel = color.getFabricTier().getName() + " / " + color.getName() + " (" + color.getCode() + ")";
                MeasurementProfile profile = measurementProfileRepository
                        .findByIdAndUserId(itemReq.measurementProfileId(), customerId)
                        .orElseThrow(() -> new NotFoundException("Measurement profile not found for this customer"));
                measurementJson = writeJson(profile);
            }

            long line = unitPrice * itemReq.quantity();
            subtotal += line;
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemReq.quantity())
                    .custom(itemReq.custom())
                    .fabricColorId(itemReq.fabricColorId())
                    .fabricLabel(fabricLabel)
                    .measurementJson(measurementJson)
                    .unitPricePaisa(unitPrice)
                    .lineTotalPaisa(line)
                    .build();
            order.getItems().add(item);
        }

        if (discount > subtotal) {
            throw new BadRequestException("Discount cannot exceed the subtotal");
        }

        order.setSubtotalPaisa(subtotal);
        order.setTotalPaisa(subtotal - discount + resolvedDispatchCostPaisa);
        appendHistory(order, null, initial, "Order created in-store by staff", actorId);
        if (anyCustom) {
            appendHistory(order, initial, OrderStatus.PAYMENT_PENDING, "Awaiting payment", actorId);
            order.setStatus(OrderStatus.PAYMENT_PENDING);
        }

        return toDto(orderRepository.save(order), null);
    }

    private void appendHistory(ShopOrder order, OrderStatus from, OrderStatus to, String note, Long actorId) {
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .note(note)
                .changedBy(actorId)
                .build());
    }

    private String generateCode() {
        int n = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "JH-" + Year.now().getValue() + "-" + n;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    private Map<Long, String> branchNameCache() {
        Map<Long, String> map = new HashMap<>();
        for (Branch b : branchRepository.findAll()) {
            map.put(b.getId(), b.getName());
        }
        return map;
    }

    private String branchName(Long branchId, Map<Long, String> cache) {
        if (branchId == null) {
            return null;
        }
        if (cache != null) {
            return cache.get(branchId);
        }
        return branchRepository.findById(branchId).map(Branch::getName).orElse(null);
    }

    private OrderDtos.OrderResponse toDto(ShopOrder order, Map<Long, String> branchNames) {
        long amountPaid = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .mapToLong(Payment::getAmountPaisa)
                .sum();
        long balanceDue = Math.max(0, order.getTotalPaisa() - amountPaid);

        return new OrderDtos.OrderResponse(
                order.getId(),
                order.getPublicCode(),
                order.getOrderType(),
                order.getStatus(),
                order.getCurrency(),
                order.getSubtotalPaisa(),
                order.getDiscountPaisa(),
                order.getDispatchCostPaisa(),
                order.getTotalPaisa(),
                amountPaid,
                balanceDue,
                order.getWhatsappPhone(),
                order.getCustomerNote(),
                order.getCreatedBranchId(),
                branchName(order.getCreatedBranchId(), branchNames),
                order.getDispatchBranchId(),
                branchName(order.getDispatchBranchId(), branchNames),
                order.getExpectedDeliveryDate(),
                order.getItems().stream().map(i -> new OrderDtos.OrderItemResponse(
                        i.getProductId(), i.getProductName(), i.getQuantity(), i.isCustom(),
                        i.getFabricLabel(), i.getUnitPricePaisa(), i.getLineTotalPaisa()
                )).toList(),
                order.getStatusHistory().stream().map(this::toHistory).toList(),
                order.getCreatedAt()
        );
    }

    private OrderDtos.StatusHistoryResponse toHistory(OrderStatusHistory h) {
        return new OrderDtos.StatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getCreatedAt());
    }
}
