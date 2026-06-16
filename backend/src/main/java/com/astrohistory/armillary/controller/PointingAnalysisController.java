package com.astrohistory.armillary.controller;

import com.astrohistory.armillary.entity.PointingAnalysis;
import com.astrohistory.armillary.service.PointingAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/pointing")
@RequiredArgsConstructor
public class PointingAnalysisController {

    private final PointingAnalysisService analysisService;

    @PostMapping("/analyze")
    public ResponseEntity<PointingAnalysis> analyzePointing(
            @PathVariable UUID instrumentId,
            @RequestParam double targetRa,
            @RequestParam double targetDec,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime analysisTime) {
        return ResponseEntity.ok(
                analysisService.analyzePointing(instrumentId, targetRa, targetDec, analysisTime));
    }

    @PostMapping("/analyze-current")
    public ResponseEntity<PointingAnalysis> analyzeCurrentPointing(
            @PathVariable UUID instrumentId) {
        return ResponseEntity.ok(analysisService.analyzeCurrentPointing(instrumentId));
    }

    @GetMapping
    public ResponseEntity<List<PointingAnalysis>> getAnalysisHistory(
            @PathVariable UUID instrumentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ResponseEntity.ok(
                analysisService.getAnalysisByTimeRange(instrumentId, startTime, endTime));
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<PointingAnalysis>> getAnalysisPaged(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                analysisService.getAnalysisPaged(instrumentId, page, size));
    }

    @GetMapping("/latest")
    public ResponseEntity<PointingAnalysis> getLatestAnalysis(
            @PathVariable UUID instrumentId) {
        Optional<PointingAnalysis> analysis = analysisService.getLatestAnalysis(instrumentId);
        return analysis.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
