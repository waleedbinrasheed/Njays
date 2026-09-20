package com.menswear.legacyimport;

import com.menswear.catalog.entity.Product;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import com.menswear.common.enums.PaymentMethod;
import com.menswear.common.enums.PaymentStatus;
import com.menswear.common.enums.Role;
import com.menswear.identity.entity.User;
import com.menswear.identity.repo.UserRepository;
import com.menswear.identity.service.AuthService;
import com.menswear.orders.entity.OrderItem;
import com.menswear.orders.entity.OrderStatusHistory;
import com.menswear.orders.entity.ShopOrder;
import com.menswear.orders.repo.OrderRepository;
import com.menswear.payments.entity.Payment;
import com.menswear.payments.repo.PaymentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Imports exactly one legacy order in its own dedicated transaction
 * (REQUIRES_NEW), so a failure on one malformed row can't poison the shared
 * persistence context or roll back orders already committed earlier in the
 * same batch. Called only through LegacyImportService (which is what makes
 * REQUIRES_NEW actually apply — Spring's transaction proxy only intercepts
 * calls that come in from another bean, not self-invocation).
 */
@Component
public class LegacyOrderImportWorker {

    private static final String LEGACY_PRODUCT_SLUG = "legacy-custom-stitched-suit";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public LegacyOrderImportWorker(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public enum Outcome { CREATED, SKIPPED_DUPLICATE }

    public record Result(Outcome outcome, boolean customerCreated, String warning) {}

    @Transactional
    public Product getOrCreateLegacyProduct() {
        return productRepository.findBySlug(LEGACY_PRODUCT_SLUG).orElseGet(() -> {
            Product product = Product.builder()
                    .name("Custom Stitched Suit (Legacy)")
                    .slug(LEGACY_PRODUCT_SLUG)
                    .description("Placeholder product for orders backfilled from the pre-app paper/Excel ledger.")
                    .basePricePaisa(0L)
                    .currency("PKR")
                    .supportsCustom(true)
                    .active(false)
                    .build();
            return productRepository.save(product);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result importOne(LegacyImportDtos.OrderRequest req, Long legacyProductId, String legacyProductName) {
        if (orderRepository.existsByLegacyRef(req.legacyRef())) {
            return new Result(Outcome.SKIPPED_DUPLICATE, false, null);
        }

        String normalizedPhone = AuthService.normalizePhone(req.rawPhone());
        boolean phoneUsable = normalizedPhone != null && normalizedPhone.length() == 12 && normalizedPhone.startsWith("92");

        boolean customerCreated = false;
        String warning = null;
        User customer;
        if (phoneUsable) {
            Optional<User> existing = userRepository.findByPhone(normalizedPhone);
            if (existing.isPresent()) {
                customer = existing.get();
            } else {
                customer = createCustomer(req.customerName(), normalizedPhone, localFormat(normalizedPhone));
                customerCreated = true;
            }
        } else {
            // Unusable phone (e.g. "0", too few/many digits): never merge with another
            // customer on a shared bad key. Give it its own record, raw value preserved
            // as the name-visible identity only, no working phone login.
            customer = createCustomer(req.customerName(), null, req.rawPhone());
            customerCreated = true;
            warning = "Order " + req.legacyRef() + ": phone '" + req.rawPhone()
                    + "' is not a usable mobile number; customer created without phone login.";
        }

        Instant historicalDate = parseHistoricalDate(req.orderDate());
        ShopOrder order = buildOrder(customer, req, legacyProductId, legacyProductName);
        order = orderRepository.save(order);

        if (req.paidPaisa() != null && req.paidPaisa() > 0) {
            Payment payment = Payment.builder()
                    .orderId(order.getId())
                    .method(PaymentMethod.CASH)
                    .status(PaymentStatus.COMPLETED)
                    .amountPaisa(req.paidPaisa())
                    .currency("PKR")
                    .idempotencyKey("legacy-" + req.legacyRef())
                    .confirmedAt(historicalDate)
                    .build();
            Payment savedPayment = paymentRepository.save(payment);
            if (historicalDate != null) {
                paymentRepository.backdate(savedPayment.getId(), historicalDate);
            }
        }

        if (historicalDate != null) {
            orderRepository.backdate(order.getId(), historicalDate);
        }

        return new Result(Outcome.CREATED, customerCreated, warning);
    }

    private User createCustomer(String fullName, String phone, String passwordPlain) {
        User user = User.builder()
                .fullName(fullName == null || fullName.isBlank() ? "Customer" : fullName.trim())
                .phone(phone)
                .email(null)
                .passwordHash(passwordEncoder.encode(passwordPlain))
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    private ShopOrder buildOrder(User customer, LegacyImportDtos.OrderRequest req, Long legacyProductId, String legacyProductName) {
        long subtotal = 0;
        ShopOrder order = ShopOrder.builder()
                .publicCode(generateCode())
                .userId(customer.getId())
                .orderType(OrderType.CUSTOM)
                .status(OrderStatus.PAYMENT_PENDING)
                .currency("PKR")
                .shippingPaisa(0L)
                .shippingAddressJson("{\"line1\":\"" + escape(req.branch() != null ? req.branch() : "Turbat branch") + "\",\"city\":\"Turbat\",\"country\":\"PK\"}")
                .whatsappPhone(AuthService.normalizePhone(req.rawPhone()))
                .customerNote(buildNote(req))
                .legacyRef(req.legacyRef())
                .build();

        for (LegacyImportDtos.ItemRequest item : req.items()) {
            long fabricTotal = item.fabricTotalPaisa() != null ? item.fabricTotalPaisa() : 0;
            long stitch = item.stitchPaisa() != null ? item.stitchPaisa() : 0;
            long lineTotal = fabricTotal + stitch;
            subtotal += lineTotal;
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(legacyProductId)
                    .productName(legacyProductName)
                    .quantity(1)
                    .custom(true)
                    .fabricColorId(null)
                    .fabricLabel(item.fabricName())
                    .measurementJson(null)
                    .unitPricePaisa(lineTotal)
                    .lineTotalPaisa(lineTotal)
                    .build();
            order.getItems().add(orderItem);
        }

        // Ledger's own total is authoritative (it may not equal the sum of parsed
        // fabric/stitch lines when the sheet itself has reconciliation gaps).
        long total = req.totalPaisa() != null ? req.totalPaisa() : subtotal;
        order.setSubtotalPaisa(subtotal);
        order.setTotalPaisa(total);

        appendHistory(order, null, OrderStatus.PAYMENT_PENDING, "Imported from legacy ledger", null);

        OrderStatus finalStatus = mapStatus(req.status(), req.paidPaisa());
        if (finalStatus != OrderStatus.PAYMENT_PENDING) {
            appendHistory(order, OrderStatus.PAYMENT_PENDING, finalStatus,
                    "Legacy ledger status: " + (req.status() == null || req.status().isBlank() ? "(blank)" : req.status()), null);
            order.setStatus(finalStatus);
        }

        return order;
    }

    private OrderStatus mapStatus(String rawStatus, Long paidPaisa) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return (paidPaisa != null && paidPaisa > 0) ? OrderStatus.PAYMENT_CONFIRMED : OrderStatus.PAYMENT_PENDING;
        }
        String s = rawStatus.trim().toLowerCase();
        if (s.equals("delivered")) {
            return OrderStatus.DELIVERED;
        }
        if (s.equals("correction")) {
            return OrderStatus.IN_STITCHING;
        }
        return (paidPaisa != null && paidPaisa > 0) ? OrderStatus.PAYMENT_CONFIRMED : OrderStatus.PAYMENT_PENDING;
    }

    private String buildNote(LegacyImportDtos.OrderRequest req) {
        StringBuilder sb = new StringBuilder("Imported from legacy ledger.");
        if (req.slipNumber() != null && !req.slipNumber().isBlank()) {
            sb.append(" Slip #").append(req.slipNumber()).append('.');
        }
        if (req.suitCount() != null) {
            sb.append(" ").append(req.suitCount()).append(" suit(s).");
        }
        return sb.toString();
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

    private static Instant parseHistoricalDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String localFormat(String normalizedPhone) {
        // "923350390242" -> "03350390242"
        return normalizedPhone.startsWith("92") ? "0" + normalizedPhone.substring(2) : normalizedPhone;
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    private static String generateCode() {
        int n = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "JH-" + Year.now().getValue() + "-" + n;
    }
}
