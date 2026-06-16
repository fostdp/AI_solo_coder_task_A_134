package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.AlertDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.entity.PointingAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private static final String TOPIC_PREFIX = "/topic/";

    public void broadcastSensorData(SensorDataDTO data) {
        try {
            String topic = TOPIC_PREFIX + "sensor/" + data.getInstrumentId();
            messagingTemplate.convertAndSend(topic, data);
            log.debug("Broadcast sensor data to {}", topic);
        } catch (Exception e) {
            log.error("Failed to broadcast sensor data", e);
        }
    }

    public void broadcastFrictionSimulation(FrictionSimulation simulation) {
        try {
            UUID instrumentId = simulation.getInstrument().getId();
            String topic = TOPIC_PREFIX + "friction/" + instrumentId;

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", simulation.getId());
            payload.put("instrumentId", instrumentId);
            payload.put("axisName", simulation.getAxisName());
            payload.put("simulationTime", simulation.getSimulationTime());
            payload.put("lambdaRatio", simulation.getLambdaRatio());
            payload.put("filmThickness", simulation.getFilmThickness());
            payload.put("contactPressure", simulation.getContactPressure());
            payload.put("frictionCoefficient", simulation.getFrictionCoefficient());
            payload.put("asperityContactRatio", simulation.getAsperityContactRatio());
            payload.put("wearRate", simulation.getWearRate());
            payload.put("totalWearDepth", simulation.getTotalWearDepth());

            messagingTemplate.convertAndSend(topic, payload);
            log.debug("Broadcast friction simulation to {}", topic);
        } catch (Exception e) {
            log.error("Failed to broadcast friction simulation", e);
        }
    }

    public void broadcastPointingAnalysis(PointingAnalysis analysis) {
        try {
            UUID instrumentId = analysis.getInstrument().getId();
            String topic = TOPIC_PREFIX + "pointing/" + instrumentId;

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", analysis.getId());
            payload.put("instrumentId", instrumentId);
            payload.put("analysisTime", analysis.getAnalysisTime());
            payload.put("targetRa", analysis.getTargetRa());
            payload.put("targetDec", analysis.getTargetDec());
            payload.put("azimuthError", analysis.getAzimuthError());
            payload.put("altitudeError", analysis.getAltitudeError());
            payload.put("totalPointingError", analysis.getTotalPointingError());
            payload.put("errorSource", analysis.getRaErrorSource());
            payload.put("uncertainty", analysis.getErrorUncertainty());

            messagingTemplate.convertAndSend(topic, payload);
            log.debug("Broadcast pointing analysis to {}", topic);
        } catch (Exception e) {
            log.error("Failed to broadcast pointing analysis", e);
        }
    }

    public void broadcastAlert(AlertDTO alert) {
        try {
            String topic = TOPIC_PREFIX + "alerts/" + alert.getInstrumentId();
            messagingTemplate.convertAndSend(topic, alert);
            log.debug("Broadcast alert to {}", topic);
        } catch (Exception e) {
            log.error("Failed to broadcast alert", e);
        }
    }

    public void broadcastInstrumentStatus(UUID instrumentId, Map<String, Object> status) {
        try {
            String topic = TOPIC_PREFIX + "status/" + instrumentId;
            messagingTemplate.convertAndSend(topic, status);
            log.debug("Broadcast instrument status to {}", topic);
        } catch (Exception e) {
            log.error("Failed to broadcast instrument status", e);
        }
    }
}
