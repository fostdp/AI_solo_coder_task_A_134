package com.astrohistory.armillary.module.alarm_ws;

import com.astrohistory.armillary.config.FrictionParamsProperties;
import com.astrohistory.armillary.dto.AlertDTO;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.Alert;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.event.AlertTriggeredEvent;
import com.astrohistory.armillary.event.FrictionSimulationCompletedEvent;
import com.astrohistory.armillary.event.PointingAnalysisCompletedEvent;
import com.astrohistory.armillary.event.SensorDataReceivedEvent;
import com.astrohistory.armillary.repository.AlertRepository;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmWsModule {

    private final AlertRepository alertRepository;
    private final BearingConfigRepository bearingConfigRepository;
    private final FrictionParamsProperties paramsProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String TOPIC_PREFIX = "/topic/";
    private static final double ARCMIN_TO_DEG = 1.0 / 60.0;

    @Async("alarmExecutor")
    @EventListener
    @Transactional
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        ArmillaryInstrument instrument = event.getInstrument();
        SensorData sensorData = event.getSensorData();
        SensorDataDTO dto = event.getSensorDataDTO();

        checkWearAlert(instrument, sensorData);
        checkFrictionTorqueAlert(instrument, sensorData);
        checkPointingErrorSensorAlert(instrument, sensorData);
        broadcastSensorData(dto);
    }

    @Async("alarmExecutor")
    @EventListener
    public void onFrictionSimulationCompleted(FrictionSimulationCompletedEvent event) {
        if (event.isSuccess() && event.getSimulation() != null) {
            broadcastFrictionSimulation(event.getSimulation());
        } else {
            log.warn("[AlarmWs] 摩擦仿真失败事件: instrument={}, axis={}, error={}",
                    event.getInstrumentId(), event.getAxisName(), event.getErrorMessage());
        }
    }

    @Async("alarmExecutor")
    @EventListener
    @Transactional
    public void onPointingAnalysisCompleted(PointingAnalysisCompletedEvent event) {
        if (!event.isSuccess()) {
            log.warn("[AlarmWs] 指向分析失败事件: instrument={}, error={}",
                    event.getInstrumentId(), event.getErrorMessage());
            return;
        }

        PointingAnalysis analysis = event.getAnalysis();
        if (analysis == null) return;

        checkPointingAccuracyAlert(event.getInstrumentId(), analysis);
        broadcastPointingAnalysis(analysis);
    }

    @Async("alarmExecutor")
    @EventListener
    public void onAlertTriggered(AlertTriggeredEvent event) {
        AlertDTO alertDTO = event.getAlertDTO();
        if (alertDTO != null) {
            broadcastAlert(alertDTO);
        }
    }

    private void checkWearAlert(ArmillaryInstrument instrument, SensorData sensorData) {
        if (sensorData.getWearDepth() == null) return;

        double wearDepth = sensorData.getWearDepth().doubleValue();
        double axisThreshold = bearingConfigRepository
                .findByInstrumentIdAndAxisName(instrument.getId(), sensorData.getAxisName())
                .map(config -> config.getMaxAllowableWear() != null ?
                        config.getMaxAllowableWear().doubleValue() :
                        paramsProperties.getAlertThresholds().getMaxWearDefaultMm())
                .orElse(paramsProperties.getAlertThresholds().getMaxWearDefaultMm());

        double approachFactor = paramsProperties.getAlertThresholds().getWearApproachFactor();
        String axisName = sensorData.getAxisName();

        if (wearDepth > axisThreshold) {
            String msg = String.format("%s 轴承磨损超限: %.6f mm > %.6f mm",
                    axisName, wearDepth, axisThreshold);
            createAndPublishAlert(instrument, AlertTriggeredEvent.AlertSource.WEAR_THRESHOLD,
                    axisName, "WEAR_EXCEEDED", "WARNING", msg,
                    BigDecimal.valueOf(axisThreshold), sensorData.getWearDepth());
        } else if (wearDepth > axisThreshold * approachFactor) {
            String msg = String.format("%s 轴承磨损接近阈值: %.6f mm > %.6f mm (80%%)",
                    axisName, wearDepth, axisThreshold * approachFactor);
            createAndPublishAlert(instrument, AlertTriggeredEvent.AlertSource.WEAR_APPROACHING,
                    axisName, "WEAR_APPROACHING", "INFO", msg,
                    BigDecimal.valueOf(axisThreshold), sensorData.getWearDepth());
        }
    }

    private void checkFrictionTorqueAlert(ArmillaryInstrument instrument, SensorData sensorData) {
        if (sensorData.getFrictionTorque() == null) return;

        double torque = sensorData.getFrictionTorque().doubleValue();
        double baseline = paramsProperties.getAlertThresholds().getFrictionTorqueBaselineNm();
        double warningFactor = paramsProperties.getAlertThresholds().getFrictionTorqueWarningFactor();
        double threshold = baseline * warningFactor;

        if (torque > threshold) {
            String msg = String.format("%s 摩擦力矩异常: %.4f N·m > %.2f N·m (%.0f%%×基准)",
                    sensorData.getAxisName(), torque, threshold, warningFactor * 100);
            createAndPublishAlert(instrument, AlertTriggeredEvent.AlertSource.FRICTION_ANOMALY,
                    sensorData.getAxisName(), "FRICTION_HIGH", "WARNING", msg,
                    BigDecimal.valueOf(threshold), sensorData.getFrictionTorque());
        }
    }

    private void checkPointingErrorSensorAlert(ArmillaryInstrument instrument, SensorData sensorData) {
        BigDecimal azErr = sensorData.getPointingErrorAz();
        BigDecimal altErr = sensorData.getPointingErrorAlt();
        if (azErr == null && altErr == null) return;

        double az = azErr != null ? azErr.doubleValue() : 0.0;
        double alt = altErr != null ? altErr.doubleValue() : 0.0;
        double totalDeg = Math.sqrt(az * az + alt * alt);
        double totalArcmin = totalDeg / ARCMIN_TO_DEG;
        double thresholdArcmin = paramsProperties.getAlertThresholds().getMaxPointingErrorArcmin();

        if (totalArcmin > thresholdArcmin) {
            String msg = String.format("%s 传感器指向误差超限: %.4f' > %.2f'",
                    sensorData.getAxisName(), totalArcmin, thresholdArcmin);
            createAndPublishAlert(instrument, AlertTriggeredEvent.AlertSource.POINTING_ERROR_SENSOR,
                    sensorData.getAxisName(), "POINTING_ERROR", "WARNING", msg,
                    BigDecimal.valueOf(thresholdArcmin), BigDecimal.valueOf(totalArcmin));
        }
    }

    private void checkPointingAccuracyAlert(UUID instrumentId, PointingAnalysis analysis) {
        if (analysis.getInstrument() == null) return;

        double totalDeg = analysis.getTotalPointingError() != null ?
                analysis.getTotalPointingError().doubleValue() : 0.0;
        double totalArcmin = totalDeg / ARCMIN_TO_DEG;
        double thresholdArcmin = paramsProperties.getAlertThresholds().getMaxPointingErrorArcmin();

        if (totalArcmin > thresholdArcmin) {
            String msg = String.format("指向精度分析超限: 总误差 %.4f' > 阈值 %.2f'",
                    totalArcmin, thresholdArcmin);
            createAndPublishAlert(analysis.getInstrument(),
                    AlertTriggeredEvent.AlertSource.POINTING_ERROR_ANALYSIS,
                    null, "POINTING_ERROR", "WARNING", msg,
                    BigDecimal.valueOf(thresholdArcmin), BigDecimal.valueOf(totalArcmin));
        }
    }

    private void createAndPublishAlert(ArmillaryInstrument instrument,
                                       AlertTriggeredEvent.AlertSource source,
                                       String axisName,
                                       String alertType,
                                       String alertLevel,
                                       String message,
                                       BigDecimal threshold,
                                       BigDecimal actual) {
        Alert alert = Alert.builder()
                .instrument(instrument)
                .alertType(alertType)
                .alertLevel(alertLevel)
                .axisName(axisName)
                .message(message)
                .thresholdValue(threshold)
                .actualValue(actual)
                .isAcknowledged(false)
                .build();
        alert = alertRepository.save(alert);
        log.warn("[AlarmWs] 告警生成: [{}] {} - {}", alertLevel, alertType, message);

        AlertDTO dto = AlertDTO.builder()
                .id(alert.getId())
                .instrumentId(instrument.getId())
                .instrumentName(instrument.getName())
                .alertType(alert.getAlertType())
                .alertLevel(alert.getAlertLevel())
                .axisName(alert.getAxisName())
                .message(alert.getMessage())
                .thresholdValue(alert.getThresholdValue())
                .actualValue(alert.getActualValue())
                .isAcknowledged(alert.getIsAcknowledged())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .createdAt(alert.getCreatedAt())
                .build();

        eventPublisher.publishEvent(new AlertTriggeredEvent(
                this, instrument.getId(), axisName, source,
                alert, dto, threshold.doubleValue(), actual.doubleValue()
        ));
    }

    private void broadcastSensorData(SensorDataDTO data) {
        safeSend(TOPIC_PREFIX + "sensor/" + data.getInstrumentId(), data);
    }

    private void broadcastFrictionSimulation(FrictionSimulation sim) {
        UUID instrumentId = sim.getInstrument().getId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", sim.getId());
        payload.put("instrumentId", instrumentId);
        payload.put("axisName", sim.getAxisName());
        payload.put("simulationTime", sim.getSimulationTime());
        payload.put("lambdaRatio", sim.getLambdaRatio());
        payload.put("filmThickness", sim.getFilmThickness());
        payload.put("contactPressure", sim.getContactPressure());
        payload.put("frictionCoefficient", sim.getFrictionCoefficient());
        payload.put("asperityContactRatio", sim.getAsperityContactRatio());
        payload.put("wearRate", sim.getWearRate());
        payload.put("totalWearDepth", sim.getTotalWearDepth());
        safeSend(TOPIC_PREFIX + "friction/" + instrumentId, payload);
    }

    private void broadcastPointingAnalysis(PointingAnalysis analysis) {
        UUID instrumentId = analysis.getInstrument().getId();
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
        payload.put("errorUncertainty", analysis.getErrorUncertainty());
        payload.put("perpendicularityErrorEquatorial", analysis.getPerpendicularityErrorEquatorial());
        payload.put("perpendicularityErrorAltaz", analysis.getPerpendicularityErrorAltaz());
        payload.put("axialRunoutError", analysis.getAxialRunoutError());
        payload.put("radialRunoutError", analysis.getRadialRunoutError());
        payload.put("geometricErrorContribution", analysis.getGeometricErrorContribution());
        safeSend(TOPIC_PREFIX + "pointing/" + instrumentId, payload);
    }

    private void broadcastAlert(AlertDTO alert) {
        safeSend(TOPIC_PREFIX + "alerts/" + alert.getInstrumentId(), alert);
    }

    private void safeSend(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend(topic, payload);
            log.trace("[AlarmWs] STOMP推送: topic={}", topic);
        } catch (Exception e) {
            log.error("[AlarmWs] STOMP推送失败: topic={}", topic, e);
        }
    }
}
