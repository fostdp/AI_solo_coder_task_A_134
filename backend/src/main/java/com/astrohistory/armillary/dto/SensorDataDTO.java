package com.astrohistory.armillary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataDTO {

    private UUID instrumentId;
    private String instrumentName;
    private String axisName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private BigDecimal rotationalSpeed;
    private BigDecimal frictionTorque;
    private BigDecimal wearDepth;
    private BigDecimal pointingErrorAz;
    private BigDecimal pointingErrorAlt;
    private BigDecimal temperature;
    private BigDecimal loadRadial;
    private BigDecimal loadAxial;
}
