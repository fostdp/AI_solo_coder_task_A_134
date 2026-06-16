package com.astrohistory.armillary.event;

import com.astrohistory.armillary.entity.FrictionSimulation;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class FrictionSimulationCompletedEvent extends ApplicationEvent {

    private final UUID instrumentId;
    private final String axisName;
    private final transient FrictionSimulation simulation;
    private final LocalDateTime eventTime;
    private final boolean success;
    private final String errorMessage;

    public FrictionSimulationCompletedEvent(Object source,
                                            UUID instrumentId,
                                            String axisName,
                                            FrictionSimulation simulation) {
        super(source);
        this.instrumentId = instrumentId;
        this.axisName = axisName;
        this.simulation = simulation;
        this.eventTime = LocalDateTime.now();
        this.success = true;
        this.errorMessage = null;
    }

    public FrictionSimulationCompletedEvent(Object source,
                                            UUID instrumentId,
                                            String axisName,
                                            String errorMessage) {
        super(source);
        this.instrumentId = instrumentId;
        this.axisName = axisName;
        this.simulation = null;
        this.eventTime = LocalDateTime.now();
        this.success = false;
        this.errorMessage = errorMessage;
    }
}
