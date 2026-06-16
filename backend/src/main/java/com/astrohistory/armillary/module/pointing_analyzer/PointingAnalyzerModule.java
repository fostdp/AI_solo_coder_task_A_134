package com.astrohistory.armillary.module.pointing_analyzer;

import com.astrohistory.armillary.config.GeometricErrorProperties;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.event.PointingAnalysisCompletedEvent;
import com.astrohistory.armillary.event.PointingAnalysisRequestedEvent;
import com.astrohistory.armillary.event.SensorDataReceivedEvent;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.PointingAnalysisRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.PointingAccuracyModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointingAnalyzerModule {

    private final PointingAccuracyModel pointingModel;
    private final ArmillaryInstrumentRepository instrumentRepository;
    private final BearingConfigRepository bearingConfigRepository;
    private final SensorDataRepository sensorDataRepository;
    private final PointingAnalysisRepository analysisRepository;
    private final GeometricErrorProperties geometricProperties;
    private final ApplicationEventPublisher eventPublisher;

    private volatile int sensorDataCount = 0;
    private static final int AUTO_ANALYZE_INTERVAL = 10;

    @Async("pointingExecutor")
    @EventListener
    @Transactional
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        sensorDataCount++;
        if (sensorDataCount % AUTO_ANALYZE_INTERVAL == 0) {
            double defaultRa = calculateCurrentRightAscension();
            double defaultDec = geometricProperties
                    .getErrorPropagation().getObservingSite().getDefaultDeclinationDeg();

            PointingAnalysisRequestedEvent request = new PointingAnalysisRequestedEvent(
                    this, event.getInstrumentId(), defaultRa, defaultDec,
                    LocalDateTime.now(), "AUTO_" + AUTO_ANALYZE_INTERVAL + "_SAMPLES"
            );
            log.info("[PointingAnalyzer] 每{}条数据自动触发指向分析", AUTO_ANALYZE_INTERVAL);
            handlePointingRequest(request);
        }
    }

    @Async("pointingExecutor")
    @EventListener
    @Transactional
    public void onPointingAnalysisRequested(PointingAnalysisRequestedEvent event) {
        handlePointingRequest(event);
    }

    private void handlePointingRequest(PointingAnalysisRequestedEvent event) {
        UUID instrumentId = event.getInstrumentId();
        double targetRa = event.getTargetRa();
        double targetDec = event.getTargetDec();
        LocalDateTime analysisTime = event.getAnalysisTime();

        log.debug("[PointingAnalyzer] 开始指向分析: instrument={}, RA={:.4f}°, Dec={:.4f}°, 请求者={}",
                instrumentId, targetRa, targetDec, event.getRequester());

        try {
            ArmillaryInstrument instrument = instrumentRepository.findById(instrumentId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "设备不存在: " + instrumentId));

            List<BearingConfig> bearingConfigs = bearingConfigRepository
                    .findByInstrumentId(instrumentId);

            List<SensorData> latestSensorData = sensorDataRepository
                    .findLatestForAllAxes(instrumentId);

            if (latestSensorData.isEmpty()) {
                throw new IllegalStateException("无可用于分析的传感器数据");
            }

            PointingAccuracyModel.PointingAnalysisResult result =
                    pointingModel.analyze(targetRa, targetDec,
                            latestSensorData, bearingConfigs, analysisTime);

            PointingAnalysis analysis = result.toEntity(instrument, analysisTime);
            analysis = analysisRepository.save(analysis);

            log.info("[PointingAnalyzer] 指向分析完成: instrument={}, " +
                            "方位误差={:.4f}', 高度误差={:.4f}', 总误差={:.4f}'",
                    instrument.getName(),
                    analysis.getAzimuthError().doubleValue() * 60,
                    analysis.getAltitudeError().doubleValue() * 60,
                    analysis.getTotalPointingError().doubleValue() * 60);

            eventPublisher.publishEvent(new PointingAnalysisCompletedEvent(
                    this, instrumentId, targetRa, targetDec, analysis
            ));

        } catch (Exception e) {
            log.error("[PointingAnalyzer] 指向分析失败: instrument={}", instrumentId, e);
            eventPublisher.publishEvent(new PointingAnalysisCompletedEvent(
                    this, instrumentId, targetRa, targetDec, e.getMessage()
            ));
        }
    }

    private double calculateCurrentRightAscension() {
        LocalDateTime now = LocalDateTime.now();
        double julianDate = toJulianDate(now);
        double j2000 = julianDate - 2451545.0;
        double gmst = 18.697374558 + 24.06570982441908 * j2000;
        double hours = gmst % 24;
        if (hours < 0) hours += 24;
        return hours * 15.0;
    }

    private double toJulianDate(LocalDateTime dt) {
        int year = dt.getYear();
        int month = dt.getMonthValue();
        int day = dt.getDayOfMonth();
        int hour = dt.getHour();
        int minute = dt.getMinute();
        int second = dt.getSecond();

        if (month <= 2) {
            year--;
            month += 12;
        }
        int a = year / 100;
        int b = 2 - a + a / 4;
        double julianDay = Math.floor(365.25 * (year + 4716)) +
                Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
        double fracDay = (hour + minute / 60.0 + second / 3600.0) / 24.0;
        return julianDay + fracDay;
    }
}
