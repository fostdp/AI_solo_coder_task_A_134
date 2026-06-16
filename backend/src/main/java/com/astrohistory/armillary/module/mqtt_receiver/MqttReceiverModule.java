package com.astrohistory.armillary.module.mqtt_receiver;

import com.astrohistory.armillary.dto.MqttSensorMessage;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.event.SensorDataReceivedEvent;
import com.astrohistory.armillary.module.mqtt_receiver.validator.SensorDataValidator;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttReceiverModule {

    private final ObjectMapper objectMapper;
    private final SensorDataValidator validator;
    private final SensorDataRepository sensorDataRepository;
    private final ArmillaryInstrumentRepository instrumentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    @Transactional
    public void handleSensorMessage(Message<String> message) {
        String payload = message.getPayload();
        log.debug("MQTT接收器收到消息: {}", payload.length() > 200 ?
                payload.substring(0, 200) + "..." : payload);

        MqttSensorMessage sensorMessage;
        try {
            sensorMessage = objectMapper.readValue(payload, MqttSensorMessage.class);
        } catch (Exception e) {
            log.error("MQTT消息反序列化失败: {}", e.getMessage());
            return;
        }

        SensorDataValidator.ValidationResult validation = validator.validate(sensorMessage);
        if (!validation.isValid()) {
            log.error("数据校验失败，丢弃消息: instrument={}, axis={}, errors={}",
                    sensorMessage.getInstrumentId(),
                    sensorMessage.getAxisName(),
                    validation.getErrors());
            return;
        }

        UUID instrumentId = sensorMessage.getInstrumentId();
        ArmillaryInstrument instrument = instrumentRepository.findById(instrumentId).orElse(null);
        if (instrument == null) {
            log.error("设备不存在: {}", instrumentId);
            return;
        }

        SensorData sensorData = convertToEntity(sensorMessage, instrument);
        sensorData = sensorDataRepository.save(sensorData);
        log.info("传感器数据已保存: instrument={}, axis={}, time={}",
                instrument.getName(), sensorData.getAxisName(), sensorData.getTimestamp());

        SensorDataDTO dto = convertToDTO(sensorData, instrument);

        eventPublisher.publishEvent(new SensorDataReceivedEvent(
                this, instrument, sensorData, dto
        ));

        log.debug("已发布SensorDataReceivedEvent: instrument={}, axis={}",
                instrumentId, sensorMessage.getAxisName());
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

    private SensorDataDTO convertToDTO(SensorData data, ArmillaryInstrument instrument) {
        return SensorDataDTO.builder()
                .instrumentId(instrument.getId())
                .instrumentName(instrument.getName())
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
