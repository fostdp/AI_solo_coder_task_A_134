package com.astrohistory.armillary.event;

import com.astrohistory.armillary.dto.AlertDTO;
import com.astrohistory.armillary.entity.Alert;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class AlertTriggeredEvent extends ApplicationEvent {

    public enum AlertSource {
        WEAR_THRESHOLD,
        FRICTION_ANOMALY,
        POINTING_ERROR_SENSOR,
        POINTING_ERROR_ANALYSIS,
        WEAR_APPROACHING
    }

    private final UUID instrumentId;
    private final String axisName;
    private final AlertSource sourceType;
    private final transient Alert alert;
    private final transient AlertDTO alertDTO;
    private final LocalDateTime eventTime;
    private final double thresholdValue;
    private final double actualValue;

    public AlertTriggeredEvent(Object source,
                               UUID instrumentId,
                               String axisName,
                               AlertSource sourceType,
                               Alert alert,
                               AlertDTO alertDTO,
                               double thresholdValue,
                               double actualValue) {
        super(source);
        this.instrumentId = instrumentId;
        this.axisName = axisName;
        this.sourceType = sourceType;
        this.alert = alert;
        this.alertDTO = alertDTO;
        this.thresholdValue = thresholdValue;
        this.actualValue = actualValue;
        this.eventTime = LocalDateTime.now();
    }
}
