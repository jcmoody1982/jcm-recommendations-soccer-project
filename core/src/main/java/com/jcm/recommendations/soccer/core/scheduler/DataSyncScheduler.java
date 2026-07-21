package com.jcm.recommendations.soccer.core.scheduler;

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

    @Scheduled(cron = "${scheduler.cron:0 0 4 * * ?}")
    public void scheduledSync() {
        log.info("Scheduled sync triggered");
        try {
            DataSyncService.SyncSummary summary = dataSyncService.runFullSync();
            log.info("Scheduled sync completed: success={}, duration={}s", 
                    summary.success(), summary.durationSeconds());
        } catch (Exception e) {
            log.error("Scheduled sync failed: error={}", e.getMessage(), e);
        }
    }
}
