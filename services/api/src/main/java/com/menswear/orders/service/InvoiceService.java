package com.menswear.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.exception.NotFoundException;
import com.menswear.config.MenswearProperties;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.security.SecurityUtils;
import com.menswear.orders.dto.InvoiceDtos;
import com.menswear.orders.dto.OrderDtos;
import com.menswear.orders.entity.OrderItem;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InvoiceService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final MenswearProperties properties;
    private final ObjectMapper objectMapper;

    public InvoiceService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            MenswearProperties properties,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceResponse forCustomer(Long orderId) {
        ShopOrder order = orderRepository.findByIdAndUserId(orderId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return build(order);
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceResponse forAdmin(Long orderId) {
        ShopOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return build(order);
    }

    private InvoiceDtos.InvoiceResponse build(ShopOrder order) {
        User customer = userRepository.findById(order.getUserId()).orElse(null);

        InvoiceDtos.BusinessInfo business = new InvoiceDtos.BusinessInfo(
                properties.business().name(),
                blankToNull(properties.business().address()),
                blankToNull(properties.business().phone()),
                blankToNull(properties.business().email())
        );

        InvoiceDtos.CustomerInfo customerInfo = new InvoiceDtos.CustomerInfo(
                customer != null ? customer.getFullName() : null,
                order.getWhatsappPhone(),
                customer != null ? customer.getEmail() : null,
                parseAddress(order.getShippingAddressJson())
        );

        InvoiceDtos.OrderInfo orderInfo = new InvoiceDtos.OrderInfo(
                order.getPublicCode(),
                order.getOrderType(),
                order.getStatus(),
                order.getCreatedAt()
        );

        List<InvoiceDtos.InvoiceItem> items = order.getItems().stream()
                .map(this::toInvoiceItem)
                .toList();

        long amountPaid = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .mapToLong(Payment::getAmountPaisa)
                .sum();
        long balanceDue = Math.max(0, order.getTotalPaisa() - amountPaid);

        InvoiceDtos.Totals totals = new InvoiceDtos.Totals(
                order.getCurrency(),
                order.getSubtotalPaisa(),
                order.getShippingPaisa(),
                order.getTotalPaisa(),
                amountPaid,
                balanceDue
        );

        return new InvoiceDtos.InvoiceResponse(business, customerInfo, orderInfo, items, totals);
    }

    private InvoiceDtos.InvoiceItem toInvoiceItem(OrderItem item) {
        return new InvoiceDtos.InvoiceItem(
                item.getProductName(),
                item.isCustom(),
                item.getFabricLabel(),
                item.getQuantity(),
                item.getUnitPricePaisa(),
                item.getLineTotalPaisa(),
                parseMeasurements(item.getMeasurementJson())
        );
    }

    private OrderDtos.AddressDto parseAddress(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OrderDtos.AddressDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    private InvoiceDtos.MeasurementSnapshot parseMeasurements(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InvoiceDtos.MeasurementSnapshot.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
