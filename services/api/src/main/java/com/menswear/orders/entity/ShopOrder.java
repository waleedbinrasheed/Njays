package com.menswear.orders.entity;

import com.menswear.common.enums.OrderStatus;
import com.menswear.common.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_code", nullable = false, unique = true)
    private String publicCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private String currency;

    @Column(name = "subtotal_paisa", nullable = false)
    private Long subtotalPaisa;

    @Column(name = "discount_paisa", nullable = false)
    private Long discountPaisa;

    /** Snapshotted at order creation — later dispatch-cost config changes don't affect existing orders. */
    @Column(name = "dispatch_cost_paisa", nullable = false)
    private Long dispatchCostPaisa;

    @Column(name = "total_paisa", nullable = false)
    private Long totalPaisa;

    @Column(name = "shipping_address_json", nullable = false, columnDefinition = "TEXT")
    private String shippingAddressJson;

    @Column(name = "whatsapp_phone")
    private String whatsappPhone;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    /** Where the order was placed. Null for orders predating the branch concept. */
    @Column(name = "created_branch_id")
    private Long createdBranchId;

    /** Where the order will be dispatched/collected from. Null for orders predating the branch concept. */
    @Column(name = "dispatch_branch_id")
    private Long dispatchBranchId;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** Set only for orders backfilled from the legacy ledger import; null otherwise. */
    @Column(name = "legacy_ref", unique = true)
    private String legacyRef;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (currency == null) {
            currency = "PKR";
        }
        if (dispatchCostPaisa == null) {
            dispatchCostPaisa = 0L;
        }
        if (discountPaisa == null) {
            discountPaisa = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
