package com.menswear.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menswear.cart.entity.Cart;
import com.menswear.cart.entity.CartItem;
import com.menswear.cart.service.CartService;
import com.menswear.catalog.entity.FabricColor;
import com.menswear.catalog.entity.Product;
import com.menswear.catalog.repo.FabricColorRepository;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import com.menswear.identity.service.AuthService;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.measurements.entity.MeasurementProfile;
import com.menswear.measurements.repo.MeasurementProfileRepository;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.orders.entity.OrderItem;
import com.menswear.orders.entity.OrderStatusHistory;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.EnumSet;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            CartService cartService,
            ProductRepository productRepository,
            FabricColorRepository fabricColorRepository,
            MeasurementProfileRepository measurementProfileRepository,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.fabricColorRepository = fabricColorRepository;
        this.measurementProfileRepository = measurementProfileRepository;
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
                .shippingPaisa(0L)
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
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderResponse> myOrders() {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtils.currentUserId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse myOrder(Long id) {
        return toDto(orderRepository.findByIdAndUserId(id, SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Order not found")));
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
            return toDto(order);
        }
        if (STAFF_ONLY.contains(to) && actorId == null) {
            throw new BadRequestException("Staff required for this status");
        }
        order.setStatus(to);
        appendHistory(order, from, to, request.note(), actorId);
        return toDto(orderRepository.save(order));
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
        return orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDto)
                .toList();
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

    private OrderDtos.OrderResponse toDto(ShopOrder order) {
        return new OrderDtos.OrderResponse(
                order.getId(),
                order.getPublicCode(),
                order.getOrderType(),
                order.getStatus(),
                order.getCurrency(),
                order.getSubtotalPaisa(),
                order.getShippingPaisa(),
                order.getTotalPaisa(),
                order.getWhatsappPhone(),
                order.getCustomerNote(),
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
