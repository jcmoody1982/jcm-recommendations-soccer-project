package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.results.ResultsPerformanceService;
import com.jcm.recommendations.soccer.core.results.ResultsQueryService;
import com.jcm.recommendations.soccer.core.results.ResultsQueryService.DayResultsView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
@Slf4j
public class ResultsController {

    private final ResultsQueryService resultsQueryService;
    private final ResultsPerformanceService resultsPerformanceService;

    @GetMapping("/dates")
    public ResponseEntity<List<LocalDate>> listDates() {
        return ResponseEntity.ok(resultsQueryService.listSnapshotDates());
    }

    @GetMapping("/performance")
    public ResponseEntity<ResultsPerformanceService.PerformanceView> getPerformance(
            @RequestParam(required = false, defaultValue = "30d") String period) {
        try {
            return ResponseEntity.ok(resultsPerformanceService.getPerformance(period));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid performance request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<DayResultsView> getDayResults(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String outcome) {
        try {
            DayResultsView view = resultsQueryService.getDayResults(date, outcome);
            return ResponseEntity.ok(view);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid results request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
