package com.astrohistory.armillary.controller;

import com.astrohistory.armillary.dto.SensorDataDTO;
import com.astrohistory.armillary.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/sensor-data")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataService sensorDataService;

    @GetMapping
    public ResponseEntity<List<SensorDataDTO>> getSensorData(
            @PathVariable UUID instrumentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(
                sensorDataService.getSensorDataByTimeRange(instrumentId, startTime, endTime));
    }

    @GetMapping("/axis/{axisName}")
    public ResponseEntity<List<SensorDataDTO>> getSensorDataByAxis(
            @PathVariable UUID instrumentId,
            @PathVariable String axisName,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(
                sensorDataService.getSensorDataByAxisAndTimeRange(
                        instrumentId, axisName, startTime, endTime));
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<SensorDataDTO>> getSensorDataPaged(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                sensorDataService.getSensorDataPaged(instrumentId, page, size));
    }

    @GetMapping("/latest")
    public ResponseEntity<List<SensorDataDTO>> getLatestSensorData(@PathVariable UUID instrumentId) {
        return ResponseEntity.ok(sensorDataService.getLatestSensorData(instrumentId));
    }

    @GetMapping("/axes")
    public ResponseEntity<List<String>> getAvailableAxes(@PathVariable UUID instrumentId) {
        return ResponseEntity.ok(sensorDataService.getAvailableAxes(instrumentId));
    }
}
