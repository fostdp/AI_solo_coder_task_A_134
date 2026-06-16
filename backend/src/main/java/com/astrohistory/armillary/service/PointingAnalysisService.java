package com.astrohistory.armillary.service;

import com.astrohistory.armillary.config.GeometricErrorProperties;
import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.event.PointingAnalysisRequestedEvent;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.PointingAnalysisRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.PointingAccuracyModel;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointingAnalysisService {

    private final PointingAnalysisRepository analysisRepository;
    private final ArmillaryInstrumentRepository instrumentRepository;
    private final BearingConfigRepository bearingConfigRepository;
    private final SensorDataRepository sensorDataRepository;
    private final PointingAccuracyModel pointingModel;
    private final GeometricErrorProperties geometricProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PointingAnalysis analyzePointing(
            UUID instrumentId,
            double targetRa,
            double targetDec,
            LocalDateTime analysisTime) {

        log.info("[PointingAnalysisService] 收到指向分析请求: instrument={}, RA={:.4f}, Dec={:.4f}",
                instrumentId, targetRa, targetDec);

        LocalDateTime effectiveTime = analysisTime != null ? analysisTime : LocalDateTime.now();

        eventPublisher.publishEvent(new PointingAnalysisRequestedEvent(
                this, instrumentId, targetRa, targetDec, effectiveTime, "REST_API"
        ));

        ArmillaryInstrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "设备不存在: " + instrumentId));

        List<BearingConfig> bearingConfigs = bearingConfigRepository.findByInstrumentId(instrumentId);
        List<SensorData> latestSensorData = sensorDataRepository.findLatestForAllAxes(instrumentId);

        if (latestSensorData.isEmpty()) {
            throw new IllegalStateException("无可用于分析的传感器数据");
        }

        PointingAccuracyModel.PointingAnalysisResult result =
                pointingModel.analyze(targetRa, targetDec,
                        latestSensorData, bearingConfigs, effectiveTime);

        PointingAnalysis analysis = result.toEntity(instrument, effectiveTime);
        analysis = analysisRepository.save(analysis);

        log.info("[PointingAnalysisService] 同步分析完成: 总误差={:.4f}'",
                analysis.getTotalPointingError().doubleValue() * 60);

        return analysis;
    }

    @Transactional(readOnly = true)
    public List<PointingAnalysis> getAnalysisByTimeRange(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime) {
        return analysisRepository
                .findByInstrumentIdAndAnalysisTimeBetweenOrderByAnalysisTimeAsc(
                        instrumentId, startTime, endTime);
    }

    @Transactional(readOnly = true)
    public Page<PointingAnalysis> getAnalysisPaged(UUID instrumentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "analysisTime"));
        return analysisRepository.findByInstrumentIdOrderByAnalysisTimeDesc(instrumentId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<PointingAnalysis> getLatestAnalysis(UUID instrumentId) {
        return analysisRepository.findLatestByInstrumentId(instrumentId);
    }

    public PointingAnalysis analyzeCurrentPointing(UUID instrumentId) {
        double currentRa = calculateCurrentRightAscension();
        double currentDec = geometricProperties
                .getErrorPropagation().getObservingSite().getDefaultDeclinationDeg();
        return analyzePointing(instrumentId, currentRa, currentDec, LocalDateTime.now());
    }

    private double calculateCurrentRightAscension() {
        LocalDateTime now = LocalDateTime.now();
        double julianDate = toJulianDate(now);
        double j2000 = julianDate - 2451545.0;
        double gmst = 18.697374558 + 24.06570982441908 * j2000;
        return normalizeAngle(gmst) * 15.0;
    }

    private double toJulianDate(LocalDateTime dt) {
        int year = dt.getYear();
        int month = dt.getMonthValue();
        int day = dt.getDayOfMonth();
        int hour = dt.getHour();
        int minute = dt.getMinute();
        int second = dt.getSecond();
        if (month <= 2) { year--; month += 12; }
        int a = year / 100;
        int b = 2 - a + a / 4;
        double julianDay = Math.floor(365.25 * (year + 4716)) +
                Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
        double fracDay = (hour + minute / 60.0 + second / 3600.0) / 24.0;
        return julianDay + fracDay;
    }

    private double normalizeAngle(double hours) {
        hours = hours % 24;
        if (hours < 0) hours += 24;
        return hours;
    }
}
