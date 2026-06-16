package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.MqttSensorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSensorListener {

    private final ObjectMapper objectMapper;
    private final SensorDataService sensorDataService;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleSensorMessage(Message<String> message) {
        try {
            String payload = message.getPayload();
            log.debug("Received MQTT message: {}", payload);

            MqttSensorMessage sensorMessage = objectMapper.readValue(
                    payload, MqttSensorMessage.class);

            sensorDataService.processSensorData(sensorMessage);

            log.info("Processed sensor data for instrument: {}, axis: {}",
                    sensorMessage.getInstrumentId(), sensorMessage.getAxisName());

        } catch (Exception e) {
            log.error("Failed to process MQTT sensor message", e);
        }
    }
}
