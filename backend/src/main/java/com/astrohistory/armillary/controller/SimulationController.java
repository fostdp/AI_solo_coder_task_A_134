package com.astrohistory.armillary.controller;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.entity.BearingConfig;
import com.astrohistory.armillary.entity.FrictionSimulation;
import com.astrohistory.armillary.service.FrictionSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final FrictionSimulationService simulationService;

    @GetMapping("/friction")
    public ResponseEntity<List<FrictionSimulation>> getFrictionSimulations(
            @PathVariable UUID instrumentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(
                simulationService.getSimulationByTimeRange(instrumentId, startTime, endTime));
    }

    @GetMapping("/friction/axis/{axisName}")
    public ResponseEntity<List<FrictionSimulation>> getFrictionSimulationsByAxis(
            @PathVariable UUID instrumentId,
            @PathVariable String axisName,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(
                simulationService.getSimulationByAxisAndTimeRange(
                        instrumentId, axisName, startTime, endTime));
    }

    @GetMapping("/friction/latest/{axisName}")
    public ResponseEntity<FrictionSimulation> getLatestSimulation(
            @PathVariable UUID instrumentId,
            @PathVariable String axisName) {
        Optional<FrictionSimulation> simulation =
                simulationService.getLatestSimulation(instrumentId, axisName);
        return simulation.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/friction/latest")
    public ResponseEntity<List<FrictionSimulation>> getLatestSimulations(
            @PathVariable UUID instrumentId) {
        return ResponseEntity.ok(simulationService.getLatestSimulations(instrumentId));
    }

    @GetMapping("/bearing-configs")
    public ResponseEntity<List<BearingConfig>> getBearingConfigs(
            @PathVariable UUID instrumentId) {
        return ResponseEntity.ok(simulationService.getAllBearingConfigs(instrumentId));
    }

    @PostMapping("/friction/long-term")
    public ResponseEntity<List<FrictionSimulation>> runLongTermSimulation(
            @PathVariable UUID instrumentId,
            @RequestParam String axisName,
            @RequestParam(defaultValue = "1440") int durationMinutes,
            @RequestParam(defaultValue = "60") int intervalSeconds,
            @RequestBody SensorDataDTO initialSensorData) {
        return ResponseEntity.ok(
                simulationService.runLongTermSimulation(
                        instrumentId, axisName, initialSensorData,
                        durationMinutes, intervalSeconds));
    }

    @PostMapping("/friction/run")
    public ResponseEntity<FrictionSimulation> runSimulation(
            @PathVariable UUID instrumentId,
            @RequestParam String axisName,
            @RequestBody SensorDataDTO sensorData) {
        return ResponseEntity.ok(
                simulationService.runSimulation(
                        instrumentId, axisName, sensorData, LocalDateTime.now()));
    }
}
