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
public class AlertDTO {

    private Long id;
    private UUID instrumentId;
    private String instrumentName;
    private String alertType;
    private String alertLevel;
    private String axisName;
    private String message;
    private BigDecimal thresholdValue;
    private BigDecimal actualValue;
    private Boolean isAcknowledged;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime acknowledgedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
