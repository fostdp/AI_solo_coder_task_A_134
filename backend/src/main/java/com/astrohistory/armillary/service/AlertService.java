package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.AlertDTO;
import com.astrohistory.armillary.entity.Alert;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.repository.AlertRepository;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final BearingConfigRepository bearingConfigRepository;
    private final WebSocketService webSocketService;

    @Value("${simulation.alert.max-wear-threshold:0.1}")
    private double maxWearThreshold;

    @Value("${simulation.alert.max-pointing-error-arcmin:1.0}")
    private double maxPointingErrorArcmin;

    private static final double ARCMIN_TO_DEG = 1.0 / 60.0;

    @Transactional
    public void checkAndCreateAlerts(ArmillaryInstrument instrument, SensorData sensorData) {
        checkWearAlert(instrument, sensorData);
        checkFrictionTorqueAlert(instrument, sensorData);
        checkPointingErrorAlert(instrument, sensorData);
    }

    @Transactional
    public void checkPointingAccuracyAlert(ArmillaryInstrument instrument, PointingAnalysis analysis) {
        double totalError = analysis.getTotalPointingError() != null ?
                analysis.getTotalPointingError().doubleValue() : 0.0;
        double errorArcmin = totalError / ARCMIN_TO_DEG;

        if (errorArcmin > maxPointingErrorArcmin) {
            String message = String.format(
                    "指向精度超限: 总指向误差 %.4f 角分, 阈值 %.2f 角分",
                    errorArcmin, maxPointingErrorArcmin);

            createAlert(instrument, "POINTING_ERROR", "WARNING",
                    null, message,
                    BigDecimal.valueOf(maxPointingErrorArcmin),
                    BigDecimal.valueOf(errorArcmin));
        }
    }

    private void checkWearAlert(ArmillaryInstrument instrument, SensorData sensorData) {
        if (sensorData.getWearDepth() == null) return;

        double wearDepth = sensorData.getWearDepth().doubleValue();
        double axisThreshold = bearingConfigRepository
                .findByInstrumentIdAndAxisName(instrument.getId(), sensorData.getAxisName())
                .map(config -> config.getMaxAllowableWear().doubleValue())
                .orElse(maxWearThreshold);

        if (wearDepth > axisThreshold) {
            String message = String.format(
                    "%s 轴承磨损超限: 磨损深度 %.6f mm, 阈值 %.6f mm",
                    sensorData.getAxisName(), wearDepth, axisThreshold);

            createAlert(instrument, "WEAR_EXCEEDED", "WARNING",
                    sensorData.getAxisName(), message,
                    BigDecimal.valueOf(axisThreshold),
                    sensorData.getWearDepth());
        } else if (wearDepth > axisThreshold * 0.8) {
            String message = String.format(
                    "%s 轴承磨损接近阈值: 磨损深度 %.6f mm, 阈值 %.6f mm",
                    sensorData.getAxisName(), wearDepth, axisThreshold);

            createAlert(instrument, "WEAR_APPROACHING", "INFO",
                    sensorData.getAxisName(), message,
                    BigDecimal.valueOf(axisThreshold),
                    sensorData.getWearDepth());
        }
    }

    private void checkFrictionTorqueAlert(ArmillaryInstrument instrument, SensorData sensorData) {
        if (sensorData.getFrictionTorque() == null) return;

        double torque = sensorData.getFrictionTorque().doubleValue();
        double baselineTorque = 5.0;

        if (torque > baselineTorque * 2.0) {
            String message = String.format(
                    "%s 摩擦力矩异常偏高: %.4f N·m, 基准值 %.2f N·m",
                    sensorData.getAxisName(), torque, baselineTorque);

            createAlert(instrument, "FRICTION_HIGH", "WARNING",
                    sensorData.getAxisName(), message,
                    BigDecimal.valueOf(baselineTorque * 2.0),
                    sensorData.getFrictionTorque());
        }
    }

    private void checkPointingErrorAlert(ArmillaryInstrument instrument, SensorData sensorData) {
        if (sensorData.getPointingErrorAz() == null && sensorData.getPointingErrorAlt() == null) return;

        double azError = sensorData.getPointingErrorAz() != null ?
                sensorData.getPointingErrorAz().doubleValue() : 0.0;
        double altError = sensorData.getPointingErrorAlt() != null ?
                sensorData.getPointingErrorAlt().doubleValue() : 0.0;

        double totalError = Math.sqrt(azError * azError + altError * altError);
        double errorArcmin = totalError / ARCMIN_TO_DEG;

        if (errorArcmin > maxPointingErrorArcmin) {
            String message = String.format(
                    "%s 指向误差超限: %.4f 角分, 阈值 %.2f 角分",
                    sensorData.getAxisName(), errorArcmin, maxPointingErrorArcmin);

            createAlert(instrument, "POINTING_ERROR", "WARNING",
                    sensorData.getAxisName(), message,
                    BigDecimal.valueOf(maxPointingErrorArcmin),
                    BigDecimal.valueOf(errorArcmin));
        }
    }

    private void createAlert(ArmillaryInstrument instrument, String alertType,
                             String alertLevel, String axisName, String message,
                             BigDecimal threshold, BigDecimal actualValue) {
        Alert alert = Alert.builder()
                .instrument(instrument)
                .alertType(alertType)
                .alertLevel(alertLevel)
                .axisName(axisName)
                .message(message)
                .thresholdValue(threshold)
                .actualValue(actualValue)
                .isAcknowledged(false)
                .build();

        alert = alertRepository.save(alert);
        log.warn("Alert created: {}", message);

        try {
            webSocketService.broadcastAlert(toDTO(alert));
        } catch (Exception e) {
            log.error("Failed to broadcast alert via WebSocket", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AlertDTO> getActiveAlerts(UUID instrumentId) {
        return alertRepository
                .findByInstrumentIdAndIsAcknowledgedFalseOrderByCreatedAtDesc(instrumentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AlertDTO> getAlertsByTimeRange(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime) {
        return alertRepository
                .findByInstrumentIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        instrumentId, startTime, endTime)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AlertDTO> getAlertsPaged(UUID instrumentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return alertRepository
                .findByInstrumentIdOrderByCreatedAtDesc(instrumentId, pageable)
                .map(this::toDTO);
    }

    @Transactional
    public int acknowledgeAlert(Long alertId) {
        return alertRepository.acknowledgeAlert(alertId, LocalDateTime.now());
    }

    @Transactional
    public int acknowledgeAllAlerts(UUID instrumentId) {
        return alertRepository.acknowledgeAllAlerts(instrumentId, LocalDateTime.now());
    }

    public long getActiveAlertCount(UUID instrumentId) {
        return alertRepository.countByInstrumentIdAndIsAcknowledgedFalse(instrumentId);
    }

    private AlertDTO toDTO(Alert alert) {
        return AlertDTO.builder()
                .id(alert.getId())
                .instrumentId(alert.getInstrument().getId())
                .instrumentName(alert.getInstrument().getName())
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
    }
}
