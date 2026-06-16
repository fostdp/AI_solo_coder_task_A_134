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
@Table(name = "sensor_data", indexes = {
        @Index(name = "idx_sensor_data_instrument_time", columnList = "instrument_id, timestamp DESC"),
        @Index(name = "idx_sensor_data_timestamp", columnList = "timestamp DESC")
})
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private ArmillaryInstrument instrument;

    @Column(name = "axis_name", nullable = false, length = 50)
    private String axisName;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "rotational_speed", precision = 12, scale = 6)
    private BigDecimal rotationalSpeed;

    @Column(name = "friction_torque", precision = 15, scale = 8)
    private BigDecimal frictionTorque;

    @Column(name = "wear_depth", precision = 12, scale = 8)
    private BigDecimal wearDepth;

    @Column(name = "pointing_error_az", precision = 12, scale = 8)
    private BigDecimal pointingErrorAz;

    @Column(name = "pointing_error_alt", precision = 12, scale = 8)
    private BigDecimal pointingErrorAlt;

    @Column(name = "temperature", precision = 8, scale = 4)
    private BigDecimal temperature;

    @Column(name = "load_radial", precision = 12, scale = 4)
    private BigDecimal loadRadial;

    @Column(name = "load_axial", precision = 12, scale = 4)
    private BigDecimal loadAxial;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }
}
