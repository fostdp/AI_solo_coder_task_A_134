package com.astrohistory.armillary.event;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.SensorData;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class SensorDataReceivedEvent extends ApplicationEvent {

    private final UUID instrumentId;
    private final transient ArmillaryInstrument instrument;
    private final transient SensorData sensorData;
    private final transient SensorDataDTO sensorDataDTO;
    private final LocalDateTime eventTime;
    private final String axisName;

    public SensorDataReceivedEvent(Object source,
                                   ArmillaryInstrument instrument,
                                   SensorData sensorData,
                                   SensorDataDTO sensorDataDTO) {
        super(source);
        this.instrumentId = instrument.getId();
        this.instrument = instrument;
        this.sensorData = sensorData;
        this.sensorDataDTO = sensorDataDTO;
        this.axisName = sensorData.getAxisName();
        this.eventTime = LocalDateTime.now();
    }
}
