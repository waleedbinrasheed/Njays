package com.menswear.cart.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "is_custom", nullable = false)
    private boolean custom;

    @Column(name = "fabric_color_id")
    private Long fabricColorId;

    @Column(name = "measurement_profile_id")
    private Long measurementProfileId;

    @Column(name = "unit_price_paisa", nullable = false)
    private Long unitPricePaisa;
}
