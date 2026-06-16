package com.astrohistory.armillary.event;

import com.astrohistory.armillary.entity.PointingAnalysis;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class PointingAnalysisCompletedEvent extends ApplicationEvent {

    private final UUID instrumentId;
    private final double targetRa;
    private final double targetDec;
    private final transient PointingAnalysis analysis;
    private final LocalDateTime eventTime;
    private final boolean success;
    private final String errorMessage;

    public PointingAnalysisCompletedEvent(Object source,
                                          UUID instrumentId,
                                          double targetRa,
                                          double targetDec,
                                          PointingAnalysis analysis) {
        super(source);
        this.instrumentId = instrumentId;
        this.targetRa = targetRa;
        this.targetDec = targetDec;
        this.analysis = analysis;
        this.eventTime = LocalDateTime.now();
        this.success = true;
        this.errorMessage = null;
    }

    public PointingAnalysisCompletedEvent(Object source,
                                          UUID instrumentId,
                                          double targetRa,
                                          double targetDec,
                                          String errorMessage) {
        super(source);
        this.instrumentId = instrumentId;
        this.targetRa = targetRa;
        this.targetDec = targetDec;
        this.analysis = null;
        this.eventTime = LocalDateTime.now();
        this.success = false;
        this.errorMessage = errorMessage;
    }
}
