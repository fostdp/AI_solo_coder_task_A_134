package com.astrohistory.armillary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "friction_simulation")
public class FrictionSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private ArmillaryInstrument instrument;

    @Column(name = "axis_name", nullable = false, length = 50)
    private String axisName;

    @Column(name = "simulation_time", nullable = false)
    private LocalDateTime simulationTime;

    @Column(name = "lambda_ratio", precision = 10, scale = 6)
    private BigDecimal lambdaRatio;

    @Column(name = "film_thickness", precision = 12, scale = 8)
    private BigDecimal filmThickness;

    @Column(name = "contact_pressure", precision = 15, scale = 4)
    private BigDecimal contactPressure;

    @Column(name = "friction_coefficient", precision = 10, scale = 8)
    private BigDecimal frictionCoefficient;

    @Column(name = "asperity_contact_ratio", precision = 10, scale = 6)
    private BigDecimal asperityContactRatio;

    @Column(name = "wear_rate", precision = 15, scale = 10)
    private BigDecimal wearRate;

    @Column(name = "total_wear_depth", precision = 12, scale = 8)
    private BigDecimal totalWearDepth;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }
}
