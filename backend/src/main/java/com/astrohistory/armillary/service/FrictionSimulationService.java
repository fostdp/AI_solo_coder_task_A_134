package com.astrohistory.armillary.service;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrictionSimulationService {

    private final FrictionSimulationRepository simulationRepository;
    private final BearingConfigRepository bearingConfigRepository;
    private final BearingFrictionModel frictionModel;
    private final WebSocketService webSocketService;

    @Transactional
    public FrictionSimulation runSimulation(
            UUID instrumentId, String axisName,
            SensorDataDTO sensorData, LocalDateTime simulationTime) {

        BearingConfig config = bearingConfigRepository
                .findByInstrumentIdAndAxisName(instrumentId, axisName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bearing config not found for axis: " + axisName));

        double accumulatedWear = getAccumulatedWear(instrumentId, axisName);

        BearingFrictionModel.FrictionSimulationResult result =
                frictionModel.simulate(config, sensorData, accumulatedWear, simulationTime);

        FrictionSimulation simulation = result.toEntity(config, simulationTime);
        simulation = simulationRepository.save(simulation);

        try {
            webSocketService.broadcastFrictionSimulation(simulation);
        } catch (Exception e) {
            log.error("Failed to broadcast simulation via WebSocket", e);
        }

        return simulation;
    }

    @Transactional(readOnly = true)
    public List<FrictionSimulation> getSimulationByTimeRange(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime) {
        return simulationRepository
                .findByInstrumentIdAndSimulationTimeBetweenOrderBySimulationTimeAsc(
                        instrumentId, startTime, endTime);
    }

    @Transactional(readOnly = true)
    public List<FrictionSimulation> getSimulationByAxisAndTimeRange(
            UUID instrumentId, String axisName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return simulationRepository
                .findByInstrumentIdAndAxisNameAndSimulationTimeBetweenOrderBySimulationTimeAsc(
                        instrumentId, axisName, startTime, endTime);
    }

    @Transactional(readOnly = true)
    public Optional<FrictionSimulation> getLatestSimulation(UUID instrumentId, String axisName) {
        return simulationRepository.findLatestByInstrumentIdAndAxisName(instrumentId, axisName);
    }

    @Transactional(readOnly = true)
    public List<FrictionSimulation> getLatestSimulations(UUID instrumentId) {
        return simulationRepository.findLatestForAllAxes(instrumentId);
    }

    @Transactional(readOnly = true)
    public List<BearingConfig> getAllBearingConfigs(UUID instrumentId) {
        return bearingConfigRepository.findByInstrumentId(instrumentId);
    }

    private double getAccumulatedWear(UUID instrumentId, String axisName) {
        return simulationRepository
                .findLatestByInstrumentIdAndAxisName(instrumentId, axisName)
                .map(FrictionSimulation::getTotalWearDepth)
                .map(BigDecimal::doubleValue)
                .orElse(0.0);
    }

    public List<FrictionSimulation> runLongTermSimulation(
            UUID instrumentId, String axisName,
            SensorDataDTO initialSensorData,
            int durationMinutes, int intervalSeconds) {

        BearingConfig config = bearingConfigRepository
                .findByInstrumentIdAndAxisName(instrumentId, axisName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bearing config not found for axis: " + axisName));

        double accumulatedWear = getAccumulatedWear(instrumentId, axisName);
        LocalDateTime startTime = LocalDateTime.now();
        int totalSteps = (durationMinutes * 60) / intervalSeconds;

        List<FrictionSimulation> results = new java.util.ArrayList<>();

        for (int i = 0; i < totalSteps; i++) {
            LocalDateTime simTime = startTime.plusSeconds((long) i * intervalSeconds);

            double timeFactor = 1.0 + (i * intervalSeconds) / 3600.0;
            SensorDataDTO adjustedData = adjustSensorData(initialSensorData, timeFactor);

            BearingFrictionModel.FrictionSimulationResult result =
                    frictionModel.simulate(config, adjustedData, accumulatedWear, simTime);

            FrictionSimulation simulation = result.toEntity(config, simTime);
            simulation = simulationRepository.save(simulation);
            results.add(simulation);

            accumulatedWear = result.getTotalWearDepth();
        }

        return results;
    }

    private SensorDataDTO adjustSensorData(SensorDataDTO baseData, double timeFactor) {
        double wearFactor = 1.0 + 0.1 * Math.log10(timeFactor);

        return SensorDataDTO.builder()
                .instrumentId(baseData.getInstrumentId())
                .instrumentName(baseData.getInstrumentName())
                .axisName(baseData.getAxisName())
                .timestamp(baseData.getTimestamp())
                .rotationalSpeed(baseData.getRotationalSpeed())
                .frictionTorque(baseData.getFrictionTorque() != null ?
                        baseData.getFrictionTorque().multiply(BigDecimal.valueOf(wearFactor)) : null)
                .wearDepth(baseData.getWearDepth())
                .pointingErrorAz(baseData.getPointingErrorAz())
                .pointingErrorAlt(baseData.getPointingErrorAlt())
                .temperature(baseData.getTemperature() != null ?
                        baseData.getTemperature().add(BigDecimal.valueOf(0.5 * Math.log10(timeFactor))) : null)
                .loadRadial(baseData.getLoadRadial())
                .loadAxial(baseData.getLoadAxial())
                .build();
    }
}
