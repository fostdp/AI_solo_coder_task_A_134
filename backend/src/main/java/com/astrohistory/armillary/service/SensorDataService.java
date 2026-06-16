package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.MqttSensorMessage;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorDataRepository sensorDataRepository;
    private final ArmillaryInstrumentRepository instrumentRepository;
    private final FrictionSimulationService frictionSimulationService;
    private final AlertService alertService;
    private final WebSocketService webSocketService;

    @Transactional
    public SensorData processSensorData(MqttSensorMessage message) {
        ArmillaryInstrument instrument = instrumentRepository.findById(message.getInstrumentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Instrument not found: " + message.getInstrumentId()));

        SensorData sensorData = SensorData.builder()
                .instrument(instrument)
                .axisName(message.getAxisName())
                .timestamp(message.getTimestamp() != null ? message.getTimestamp() : LocalDateTime.now())
                .rotationalSpeed(message.getRotationalSpeed())
                .frictionTorque(message.getFrictionTorque())
                .wearDepth(message.getWearDepth())
                .pointingErrorAz(message.getPointingErrorAz())
                .pointingErrorAlt(message.getPointingErrorAlt())
                .temperature(message.getTemperature())
                .loadRadial(message.getLoadRadial())
                .loadAxial(message.getLoadAxial())
                .build();

        sensorData = sensorDataRepository.save(sensorData);

        try {
            frictionSimulationService.runSimulation(instrument.getId(), message.getAxisName(),
                    toDTO(sensorData), sensorData.getTimestamp());
        } catch (Exception e) {
            log.error("Failed to run friction simulation", e);
        }

        try {
            alertService.checkAndCreateAlerts(instrument, sensorData);
        } catch (Exception e) {
            log.error("Failed to check alerts", e);
        }

        try {
            webSocketService.broadcastSensorData(toDTO(sensorData));
        } catch (Exception e) {
            log.error("Failed to broadcast sensor data via WebSocket", e);
        }

        return sensorData;
    }

    public List<SensorDataDTO> getSensorDataByTimeRange(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime) {
        return sensorDataRepository
                .findByInstrumentIdAndTimestampBetweenOrderByTimestampAsc(
                        instrumentId, startTime, endTime)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SensorDataDTO> getSensorDataByAxisAndTimeRange(
            UUID instrumentId, String axisName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return sensorDataRepository
                .findByInstrumentIdAndAxisNameAndTimestampBetweenOrderByTimestampAsc(
                        instrumentId, axisName, startTime, endTime)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Page<SensorDataDTO> getSensorDataPaged(UUID instrumentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return sensorDataRepository
                .findByInstrumentIdOrderByTimestampDesc(instrumentId, pageable)
                .map(this::toDTO);
    }

    public List<SensorDataDTO> getLatestSensorData(UUID instrumentId) {
        return sensorDataRepository
                .findLatestForAllAxes(instrumentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<String> getAvailableAxes(UUID instrumentId) {
        return sensorDataRepository.findDistinctAxisNamesByInstrumentId(instrumentId);
    }

    private SensorDataDTO toDTO(SensorData data) {
        return SensorDataDTO.builder()
                .instrumentId(data.getInstrument().getId())
                .instrumentName(data.getInstrument().getName())
                .axisName(data.getAxisName())
                .timestamp(data.getTimestamp())
                .rotationalSpeed(data.getRotationalSpeed())
                .frictionTorque(data.getFrictionTorque())
                .wearDepth(data.getWearDepth())
                .pointingErrorAz(data.getPointingErrorAz())
                .pointingErrorAlt(data.getPointingErrorAlt())
                .temperature(data.getTemperature())
                .loadRadial(data.getLoadRadial())
                .loadAxial(data.getLoadAxial())
                .build();
    }
}
