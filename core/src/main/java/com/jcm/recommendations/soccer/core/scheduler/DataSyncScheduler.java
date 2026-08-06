package com.jcm.recommendations.soccer.core.scheduler;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.results.RecommendationSnapshotService;
import com.jcm.recommendations.soccer.core.results.ResultsMatchIngestService;
import com.jcm.recommendations.soccer.core.results.SettlementService;
import com.jcm.recommendations.soccer.core.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DataSyncScheduler {

    private final DataSyncService dataSyncService;
    private final RecommendationService recommendationService;
    private final RecommendationSnapshotService recommendationSnapshotService;
    private final ResultsMatchIngestService resultsMatchIngestService;
    private final SettlementService settlementService;

    @Scheduled(cron = "${scheduler.cron:0 0 4 * * ?}")
    public void scheduledSync() {
        log.info("Scheduled sync triggered");
        try {
            DataSyncService.SyncSummary summary = dataSyncService.runFullSync();
            log.info("Scheduled sync completed: success={}, duration={}s",
                    summary.success(), summary.durationSeconds());

            recommendationService.evictAllCaches();
            log.info("Recommendation caches evicted after sync");

            if (summary.success()) {
                RecommendationSnapshotService.SnapshotSummary snapshot =
                        recommendationSnapshotService.snapshotToday();
                log.info("Post-sync snapshot: date={}, inserted={}, updated={}, skipped={}",
                        snapshot.snapshotDate(), snapshot.inserted(), snapshot.updated(), snapshot.skipped());
            } else {
                log.warn("Skipping daily snapshot because sync reported success=false");
            }
        } catch (Exception e) {
            log.error("Scheduled sync failed: error={}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${scheduler.results-cron:0 0 6 * * ?}")
    public void scheduledResultsIngestAndSettle() {
        log.info("Scheduled results ingest/settle triggered");
        try {
            ResultsMatchIngestService.IngestSummary ingest = resultsMatchIngestService.ingestPendingResults();
            log.info("Scheduled ingest: dates={}, upserted={}, touched={}",
                    ingest.datesProcessed(), ingest.matchesUpserted(), ingest.touchedFixtureIds().size());

            SettlementService.SettlementSummary settlement = settlementService.settlePending();
            log.info("Scheduled settlement: examined={}, resolved={}, pending={}",
                    settlement.pendingExamined(), settlement.resolved(), settlement.stillPending());
        } catch (Exception e) {
            log.error("Scheduled results ingest/settle failed: error={}", e.getMessage(), e);
        }
    }
}
