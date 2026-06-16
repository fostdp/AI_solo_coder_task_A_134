package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.MqttSensorMessage;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.event.SensorDataReceivedEvent;
import com.astrohistory.armillary.module.mqtt_receiver.validator.SensorDataValidator;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    private final SensorDataValidator validator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SensorData processSensorData(MqttSensorMessage message) {
        log.info("[SensorDataService] REST API接收到传感器数据: instrument={}, axis={}",
                message.getInstrumentId(), message.getAxisName());

        SensorDataValidator.ValidationResult validation = validator.validate(message);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(
                    "数据校验失败: " + String.join("; ", validation.getErrors()));
        }

        ArmillaryInstrument instrument = instrumentRepository.findById(message.getInstrumentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "设备不存在: " + message.getInstrumentId()));

        SensorData sensorData = convertToEntity(message, instrument);
        sensorData = sensorDataRepository.save(sensorData);
        log.info("[SensorDataService] 传感器数据已入库: id={}, axis={}",
                sensorData.getId(), sensorData.getAxisName());

        SensorDataDTO dto = convertToDTO(sensorData);

        eventPublisher.publishEvent(new SensorDataReceivedEvent(
                this, instrument, sensorData, dto
        ));
        log.debug("[SensorDataService] 已发布SensorDataReceivedEvent");

        return sensorData;
    }

    public List<SensorDataDTO> getSensorDataByTimeRange(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime) {
        return sensorDataRepository
                .findByInstrumentIdAndTimestampBetweenOrderByTimestampAsc(
                        instrumentId, startTime, endTime)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SensorDataDTO> getSensorDataByAxisAndTimeRange(
            UUID instrumentId, String axisName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return sensorDataRepository
                .findByInstrumentIdAndAxisNameAndTimestampBetweenOrderByTimestampAsc(
                        instrumentId, axisName, startTime, endTime)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Page<SensorDataDTO> getSensorDataPaged(UUID instrumentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return sensorDataRepository
                .findByInstrumentIdOrderByTimestampDesc(instrumentId, pageable)
                .map(this::convertToDTO);
    }

    public List<SensorDataDTO> getLatestSensorData(UUID instrumentId) {
        return sensorDataRepository
                .findLatestForAllAxes(instrumentId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<String> getAvailableAxes(UUID instrumentId) {
        return sensorDataRepository.findDistinctAxisNamesByInstrumentId(instrumentId);
    }

    private SensorData convertToEntity(MqttSensorMessage msg, ArmillaryInstrument instrument) {
        return SensorData.builder()
                .instrument(instrument)
                .axisName(msg.getAxisName() != null ? msg.getAxisName().trim() : "未知轴")
                .timestamp(msg.getTimestamp() != null ? msg.getTimestamp() : LocalDateTime.now())
                .rotationalSpeed(msg.getRotationalSpeed())
                .frictionTorque(msg.getFrictionTorque())
                .wearDepth(msg.getWearDepth())
                .pointingErrorAz(msg.getPointingErrorAz())
                .pointingErrorAlt(msg.getPointingErrorAlt())
                .temperature(msg.getTemperature())
                .loadRadial(msg.getLoadRadial())
                .loadAxial(msg.getLoadAxial())
                .build();
    }

    private SensorDataDTO convertToDTO(SensorData data) {
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
