package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.service.DataSyncService;
import com.jcm.recommendations.soccer.core.service.FixtureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final DataSyncService dataSyncService;
    private final FixtureService fixtureService;

    @PostMapping("/sync")
    public ResponseEntity<DataSyncService.SyncSummary> triggerSync(
            @RequestParam(required = false) Long seasonId) {

        log.info("Manual sync triggered: seasonId={}", seasonId);

        if (seasonId != null) {
            FixtureService.SyncResult result = fixtureService.syncFixturesForSeason(seasonId, "Manual");
            return ResponseEntity.ok(new DataSyncService.SyncSummary(
                    0,
                    0,
                    result.newCount() + result.updatedCount(),
                    0,
                    0,
                    0,
                    true
            ));
        }

        DataSyncService.SyncSummary summary = dataSyncService.runFullSync();
        return ResponseEntity.ok(summary);
    }
}
