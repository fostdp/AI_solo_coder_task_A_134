package com.astrohistory.armillary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bearing_config")
public class BearingConfig {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private ArmillaryInstrument instrument;

    @Column(name = "axis_name", nullable = false, length = 50)
    private String axisName;

    @Column(name = "axis_type", nullable = false, length = 50)
    private String axisType;

    @Column(name = "bearing_type", length = 100)
    private String bearingType;

    @Column(name = "material", length = 100)
    private String material;

    @Column(name = "inner_ring_material", length = 100)
    private String innerRingMaterial;

    @Column(name = "outer_ring_material", length = 100)
    private String outerRingMaterial;

    @Column(name = "rolling_element_material", length = 100)
    private String rollingElementMaterial;

    @Column(name = "surface_roughness_ra", precision = 8, scale = 4)
    private BigDecimal surfaceRoughnessRa;

    @Column(name = "perpendicularity_error", precision = 10, scale = 8)
    private BigDecimal perpendicularityError;

    @Column(name = "axial_runout", precision = 10, scale = 8)
    private BigDecimal axialRunout;

    @Column(name = "radial_runout", precision = 10, scale = 8)
    private BigDecimal radialRunout;

    @Column(name = "inner_diameter", precision = 10, scale = 4)
    private BigDecimal innerDiameter;

    @Column(name = "outer_diameter", precision = 10, scale = 4)
    private BigDecimal outerDiameter;

    @Column(name = "width", precision = 10, scale = 4)
    private BigDecimal width;

    @Column(name = "initial_clearance", precision = 10, scale = 6)
    private BigDecimal initialClearance;

    @Column(name = "lubricant_viscosity", precision = 10, scale = 4)
    private BigDecimal lubricantViscosity;

    @Column(name = "elastic_modulus", precision = 15, scale = 2)
    private BigDecimal elasticModulus;

    @Column(name = "poisson_ratio", precision = 5, scale = 4)
    private BigDecimal poissonRatio;

    @Column(name = "hardness", precision = 10, scale = 2)
    private BigDecimal hardness;

    @Column(name = "wear_coefficient", precision = 10, scale = 8)
    private BigDecimal wearCoefficient;

    @Column(name = "max_allowable_wear", precision = 10, scale = 6)
    private BigDecimal maxAllowableWear;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
