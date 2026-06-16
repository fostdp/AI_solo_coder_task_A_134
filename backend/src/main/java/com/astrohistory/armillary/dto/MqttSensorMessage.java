package com.astrohistory.armillary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttSensorMessage {

    private UUID instrumentId;
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
