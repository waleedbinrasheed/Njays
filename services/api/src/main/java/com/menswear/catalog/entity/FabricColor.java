package com.menswear.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fabric_colors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FabricColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fabric_tier_id", nullable = false)
    private FabricTier fabricTier;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "hex_color")
    private String hexColor;
}
