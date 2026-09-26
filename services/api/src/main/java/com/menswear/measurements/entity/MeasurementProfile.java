package com.menswear.measurements.entity;

import com.menswear.common.enums.FitType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "measurement_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeasurementProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    /** Kept for backward compatibility; no longer collected in UI. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fit_type")
    private FitType fitType;

    @Column(nullable = false)
    private String unit;

    /** Kameez */
    @Column(name = "kameez_length")
    private BigDecimal kameezLength;
    private BigDecimal chest;
    private BigDecimal waist;
    private BigDecimal hip;
    private BigDecimal shoulder;

    @Column(name = "sleeve_length")
    private BigDecimal sleeveLength;

    @Column(name = "collar_length")
    private BigDecimal collarLength;

    /** Legacy neck field (optional) */
    private BigDecimal neck;

    /** Shalwar */
    @Column(name = "shalwar_length")
    private BigDecimal shalwarLength;

    @Column(name = "shalwar_bottom")
    private BigDecimal shalwarBottom;

    /** Style options */
    @Column(name = "back_style")
    private String backStyle;

    @Column(name = "sleeve_style")
    private String sleeveStyle;

    @Column(name = "button_style")
    private String buttonStyle;

    @Column(name = "collar_style")
    private String collarStyle;

    @Column(name = "cuff_style")
    private String cuffStyle;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (unit == null) {
            unit = "INCH";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
