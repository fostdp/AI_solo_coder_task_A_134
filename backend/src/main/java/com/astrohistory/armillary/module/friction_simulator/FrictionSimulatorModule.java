package com.astrohistory.armillary.module.friction_simulator;

import com.astrohistory.armillary.config.FrictionParamsProperties;
import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.event.FrictionSimulationCompletedEvent;
import com.astrohistory.armillary.event.SensorDataReceivedEvent;
import com.astrohistory.armillary.repository.BearingConfigRepository;
import com.astrohistory.armillary.repository.FrictionSimulationRepository;
import com.astrohistory.armillary.simulation.BearingFrictionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FrictionSimulatorModule {

    private final BearingFrictionModel frictionModel;
    private final BearingConfigRepository bearingConfigRepository;
    private final FrictionSimulationRepository simulationRepository;
    private final FrictionParamsProperties paramsProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Async("frictionExecutor")
    @EventListener
    @Transactional
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        UUID instrumentId = event.getInstrumentId();
        String axisName = event.getAxisName();
        SensorDataDTO sensorData = event.getSensorDataDTO();
        LocalDateTime simulationTime = event.getSensorData().getTimestamp();

        log.debug("[FrictionSimulator] 收到传感器数据事件: instrument={}, axis={}",
                instrumentId, axisName);

        try {
            BearingConfig config = bearingConfigRepository
                    .findByInstrumentIdAndAxisName(instrumentId, axisName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "轴承配置未找到: instrument=" + instrumentId + ", axis=" + axisName));

            double accumulatedWear = getAccumulatedWear(instrumentId, axisName);

            BearingFrictionModel.FrictionSimulationResult result =
                    frictionModel.simulate(config, sensorData, accumulatedWear, simulationTime);

            FrictionSimulation simulation = result.toEntity(config, simulationTime);
            simulation = simulationRepository.save(simulation);

            log.info("[FrictionSimulator] 摩擦仿真完成: instrument={}, axis={}, " +
                            "λ={:.4f}, h={:.4f}μm, μ={:.6f}, 磨损累计={:.6f}mm",
                    instrumentId, axisName,
                    simulation.getLambdaRatio(),
                    simulation.getFilmThickness(),
                    simulation.getFrictionCoefficient(),
                    simulation.getTotalWearDepth());

            eventPublisher.publishEvent(new FrictionSimulationCompletedEvent(
                    this, instrumentId, axisName, simulation
            ));

        } catch (Exception e) {
            log.error("[FrictionSimulator] 摩擦仿真失败: instrument={}, axis={}",
                    instrumentId, axisName, e);
            eventPublisher.publishEvent(new FrictionSimulationCompletedEvent(
                    this, instrumentId, axisName, e.getMessage()
            ));
        }
    }

    private double getAccumulatedWear(UUID instrumentId, String axisName) {
        return simulationRepository
                .findLatestByInstrumentIdAndAxisName(instrumentId, axisName)
                .map(FrictionSimulation::getTotalWearDepth)
                .map(BigDecimal::doubleValue)
                .orElse(0.0);
    }
}
