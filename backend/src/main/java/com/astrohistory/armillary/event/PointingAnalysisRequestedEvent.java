package com.astrohistory.armillary.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class PointingAnalysisRequestedEvent extends ApplicationEvent {

    private final UUID instrumentId;
    private final double targetRa;
    private final double targetDec;
    private final LocalDateTime analysisTime;
    private final String requester;

    public PointingAnalysisRequestedEvent(Object source,
                                          UUID instrumentId,
                                          double targetRa,
                                          double targetDec,
                                          LocalDateTime analysisTime,
                                          String requester) {
        super(source);
        this.instrumentId = instrumentId;
        this.targetRa = targetRa;
        this.targetDec = targetDec;
        this.analysisTime = analysisTime;
        this.requester = requester;
    }

    public PointingAnalysisRequestedEvent(Object source,
                                          UUID instrumentId,
                                          double targetRa,
                                          double targetDec,
                                          LocalDateTime analysisTime) {
        this(source, instrumentId, targetRa, targetDec, analysisTime, "SYSTEM");
    }
}
