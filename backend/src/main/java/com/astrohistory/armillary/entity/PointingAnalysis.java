package com.astrohistory.armillary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pointing_analysis")
public class PointingAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private ArmillaryInstrument instrument;

    @Column(name = "analysis_time", nullable = false)
    private LocalDateTime analysisTime;

    @Column(name = "target_ra", precision = 12, scale = 8)
    private BigDecimal targetRa;

    @Column(name = "target_dec", precision = 12, scale = 8)
    private BigDecimal targetDec;

    @Column(name = "azimuth_error", precision = 12, scale = 8)
    private BigDecimal azimuthError;

    @Column(name = "altitude_error", precision = 12, scale = 8)
    private BigDecimal altitudeError;

    @Column(name = "total_pointing_error", precision = 12, scale = 8)
    private BigDecimal totalPointingError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ra_error_source", columnDefinition = "jsonb")
    private Map<String, Object> raErrorSource;

    @Column(name = "error_uncertainty", precision = 12, scale = 8)
    BigDecimal errorUncertainty;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }
}
