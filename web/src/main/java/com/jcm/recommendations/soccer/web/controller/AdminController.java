package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.repository.FixtureOddsRepository;
import com.jcm.recommendations.soccer.core.repository.FixtureRepository;
import com.jcm.recommendations.soccer.core.results.RecommendationSnapshotService;
import com.jcm.recommendations.soccer.core.results.ResultsMatchIngestService;
import com.jcm.recommendations.soccer.core.results.SettlementService;
import com.jcm.recommendations.soccer.core.service.DataSyncService;
import com.jcm.recommendations.soccer.core.service.FixtureService;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final DataSyncService dataSyncService;
    private final FixtureService fixtureService;
    private final FixtureRepository fixtureRepository;
    private final FixtureOddsRepository fixtureOddsRepository;
    private final RecommendationService recommendationService;
    private final RecommendationSnapshotService recommendationSnapshotService;
    private final ResultsMatchIngestService resultsMatchIngestService;
    private final SettlementService settlementService;

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> triggerSync(
            @RequestParam(required = false) Long seasonId) {

        log.info("Manual sync triggered: seasonId={}", seasonId);

        DataSyncService.SyncSummary summary;
        if (seasonId != null) {
            FixtureService.SyncResult result = fixtureService.syncFixturesForSeason(seasonId, "Manual");
            summary = new DataSyncService.SyncSummary(
                    0,
                    0,
                    result.newCount() + result.updatedCount(),
                    0,
                    0,
                    0,
                    true
            );
        } else {
            summary = dataSyncService.runFullSync();
        }

        recommendationService.evictAllCaches();
        log.info("Recommendation caches evicted after manual sync");

        Map<String, Object> body = new HashMap<>();
        body.put("sync", summary);

        if (summary.success()) {
            RecommendationSnapshotService.SnapshotSummary snapshot =
                    recommendationSnapshotService.snapshotToday();
            body.put("snapshot", snapshot);
            log.info("Post-sync snapshot completed: {}", snapshot);
        } else {
            log.warn("Skipping snapshot after manual sync because success=false");
            body.put("snapshot", null);
        }

        return ResponseEntity.ok(body);
    }

    @PostMapping("/snapshot")
    public ResponseEntity<RecommendationSnapshotService.SnapshotSummary> triggerSnapshot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        RecommendationSnapshotService.SnapshotSummary summary = date == null
                ? recommendationSnapshotService.snapshotToday()
                : recommendationSnapshotService.snapshotForDate(date);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/results-ingest")
    public ResponseEntity<Map<String, Object>> triggerResultsIngest(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ResultsMatchIngestService.IngestSummary ingest = date == null
                ? resultsMatchIngestService.ingestPendingResults()
                : resultsMatchIngestService.ingestForDate(date);
        SettlementService.SettlementSummary settlement = settlementService.settlePending();
        return ResponseEntity.ok(Map.of(
                "ingest", ingest,
                "settlement", settlement
        ));
    }

    @PostMapping("/settle")
    public ResponseEntity<SettlementService.SettlementSummary> triggerSettle() {
        return ResponseEntity.ok(settlementService.settlePending());
    }

    @PostMapping("/cache/evict")
    public ResponseEntity<Map<String, String>> evictCaches() {
        log.info("Manual cache eviction triggered");
        recommendationService.evictAllCaches();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "All recommendation caches evicted"
        ));
    }

    @GetMapping("/diagnostics/odds")
    public ResponseEntity<Map<String, Object>> diagnoseOdds() {
        Map<String, Object> diagnostics = new HashMap<>();

        long now = Instant.now().getEpochSecond();
        long weekAhead = now + (7 * 86400L);

        List<Fixture> upcomingFixtures = fixtureRepository.findByDateRangeAndStatus(now, weekAhead, "incomplete");
        diagnostics.put("upcomingFixturesCount", upcomingFixtures.size());

        int fixturesWithOddsRecord = 0;
        int fixturesWithBttsOdds = 0;
        int fixturesWithMatchOdds = 0;
        int fixturesWithZeroOdds = 0;

        Map<String, Object> sampleFixtures = new HashMap<>();
        int sampleCount = 0;

        for (Fixture fixture : upcomingFixtures) {
            FixtureOdds odds = fixtureOddsRepository.findById(fixture.getId()).orElse(null);

            if (odds != null) {
                fixturesWithOddsRecord++;

                if (odds.getOddsBttsYes() != null && odds.getOddsBttsYes() > 0) {
                    fixturesWithBttsOdds++;
                }
                if (odds.getOddsFt1() != null && odds.getOddsFt1() > 0) {
                    fixturesWithMatchOdds++;
                }
                if ((odds.getOddsFt1() != null && odds.getOddsFt1() == 0) ||
                    (odds.getOddsFtX() != null && odds.getOddsFtX() == 0) ||
                    (odds.getOddsBttsYes() != null && odds.getOddsBttsYes() == 0)) {
                    fixturesWithZeroOdds++;
                }

                if (sampleCount < 3) {
                    Map<String, Object> sample = new HashMap<>();
                    sample.put("fixtureId", fixture.getId());
                    sample.put("match", fixture.getHomeTeamName() + " vs " + fixture.getAwayTeamName());
                    sample.put("oddsFt1", odds.getOddsFt1());
                    sample.put("oddsFtX", odds.getOddsFtX());
                    sample.put("oddsFt2", odds.getOddsFt2());
                    sample.put("oddsBttsYes", odds.getOddsBttsYes());
                    sample.put("oddsBttsNo", odds.getOddsBttsNo());
                    sample.put("oddsFtOver25", odds.getOddsFtOver25());
                    sample.put("oddsFtUnder25", odds.getOddsFtUnder25());
                    sampleFixtures.put("fixture" + (sampleCount + 1), sample);
                    sampleCount++;
                }
            }
        }

        diagnostics.put("fixturesWithOddsRecord", fixturesWithOddsRecord);
        diagnostics.put("fixturesWithBttsOdds", fixturesWithBttsOdds);
        diagnostics.put("fixturesWithMatchOdds", fixturesWithMatchOdds);
        diagnostics.put("fixturesWithZeroOdds", fixturesWithZeroOdds);
        diagnostics.put("sampleFixtures", sampleFixtures);

        return ResponseEntity.ok(diagnostics);
    }
}
