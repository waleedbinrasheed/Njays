package com.menswear.orders.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ShopOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "is_custom", nullable = false)
    private boolean custom;

    @Column(name = "fabric_color_id")
    private Long fabricColorId;

    @Column(name = "fabric_label")
    private String fabricLabel;

    @Column(name = "measurement_json", columnDefinition = "TEXT")
    private String measurementJson;

    @Column(name = "unit_price_paisa", nullable = false)
    private Long unitPricePaisa;

    @Column(name = "line_total_paisa", nullable = false)
    private Long lineTotalPaisa;
}
