package com.astrohistory.armillary.service;

import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.entity.SensorData;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.PointingAnalysisRepository;
import com.astrohistory.armillary.repository.SensorDataRepository;
import com.astrohistory.armillary.simulation.PointingAccuracyModel;
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
    private final WebSocketService webSocketService;
    private final AlertService alertService;

    @Transactional
    public PointingAnalysis analyzePointing(
            UUID instrumentId,
            double targetRa,
            double targetDec,
            LocalDateTime analysisTime) {

        ArmillaryInstrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Instrument not found: " + instrumentId));

        List<BearingConfig> bearingConfigs = bearingConfigRepository.findByInstrumentId(instrumentId);

        List<SensorData> latestSensorData = sensorDataRepository.findLatestForAllAxes(instrumentId);

        if (latestSensorData.isEmpty()) {
            throw new IllegalStateException("No sensor data available for analysis");
        }

        PointingAccuracyModel.PointingAnalysisResult result =
                pointingModel.analyze(
                        targetRa, targetDec,
                        latestSensorData, bearingConfigs,
                        analysisTime != null ? analysisTime : LocalDateTime.now());

        PointingAnalysis analysis = result.toEntity(instrument,
                analysisTime != null ? analysisTime : LocalDateTime.now());
        analysis = analysisRepository.save(analysis);

        try {
            alertService.checkPointingAccuracyAlert(instrument, analysis);
        } catch (Exception e) {
            log.error("Failed to check pointing accuracy alert", e);
        }

        try {
            webSocketService.broadcastPointingAnalysis(analysis);
        } catch (Exception e) {
            log.error("Failed to broadcast analysis via WebSocket", e);
        }

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
        double currentDec = calculateCurrentDeclination();
        return analyzePointing(instrumentId, currentRa, currentDec, LocalDateTime.now());
    }

    private double calculateCurrentRightAscension() {
        LocalDateTime now = LocalDateTime.now();
        double julianDate = toJulianDate(now);
        double j2000 = julianDate - 2451545.0;
        double gmst = 18.697374558 + 24.06570982441908 * j2000;
        return normalizeAngle(gmst) * 15.0;
    }

    private double calculateCurrentDeclination() {
        return 39.9;
    }

    private double toJulianDate(LocalDateTime dateTime) {
        int year = dateTime.getYear();
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();
        int hour = dateTime.getHour();
        int minute = dateTime.getMinute();
        int second = dateTime.getSecond();

        if (month <= 2) {
            year--;
            month += 12;
        }

        int a = year / 100;
        int b = 2 - a + a / 4;

        double julianDay = Math.floor(365.25 * (year + 4716)) +
                Math.floor(30.6001 * (month + 1)) +
                day + b - 1524.5;

        double fracDay = (hour + minute / 60.0 + second / 3600.0) / 24.0;

        return julianDay + fracDay;
    }

    private double normalizeAngle(double hours) {
        hours = hours % 24;
        if (hours < 0) hours += 24;
        return hours;
    }
}
