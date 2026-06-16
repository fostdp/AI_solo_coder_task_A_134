package com.astrohistory.armillary.controller;

import com.astrohistory.armillary.dto.AlertDTO;
import com.astrohistory.armillary.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/active")
    public ResponseEntity<List<AlertDTO>> getActiveAlerts(@PathVariable UUID instrumentId) {
        return ResponseEntity.ok(alertService.getActiveAlerts(instrumentId));
    }

    @GetMapping("/active/count")
    public ResponseEntity<Map<String, Long>> getActiveAlertCount(@PathVariable UUID instrumentId) {
        Map<String, Long> response = new HashMap<>();
        response.put("count", alertService.getActiveAlertCount(instrumentId));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AlertDTO>> getAlertHistory(
            @PathVariable UUID instrumentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(
                alertService.getAlertsByTimeRange(instrumentId, startTime, endTime));
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<AlertDTO>> getAlertsPaged(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                alertService.getAlertsPaged(instrumentId, page, size));
    }

    @PutMapping("/{alertId}/acknowledge")
    public ResponseEntity<Map<String, Integer>> acknowledgeAlert(
            @PathVariable UUID instrumentId,
            @PathVariable Long alertId) {
        int updated = alertService.acknowledgeAlert(alertId);
        Map<String, Integer> response = new HashMap<>();
        response.put("acknowledged", updated);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/acknowledge-all")
    public ResponseEntity<Map<String, Integer>> acknowledgeAllAlerts(
            @PathVariable UUID instrumentId) {
        int updated = alertService.acknowledgeAllAlerts(instrumentId);
        Map<String, Integer> response = new HashMap<>();
        response.put("acknowledged", updated);
        return ResponseEntity.ok(response);
    }
}
