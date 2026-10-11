package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.config.JacksonConfig;
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
import com.jcm.recommendations.soccer.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = "beta.auth.enabled=false")
class AdminControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSyncService dataSyncService;
    @MockitoBean
    private FixtureService fixtureService;
    @MockitoBean
    private FixtureRepository fixtureRepository;
    @MockitoBean
    private FixtureOddsRepository fixtureOddsRepository;
    @MockitoBean
    private RecommendationService recommendationService;
    @MockitoBean
    private RecommendationSnapshotService recommendationSnapshotService;
    @MockitoBean
    private ResultsMatchIngestService resultsMatchIngestService;
    @MockitoBean
    private SettlementService settlementService;

    private static DataSyncService.SyncSummary syncSummary(boolean success) {
        return new DataSyncService.SyncSummary(5, 2, 10, 4, 0, 0, 0, 0, 0, success);
    }

    @Test
    void successfulFullSyncEvictsCachesAndSnapshots() throws Exception {
        when(dataSyncService.runFullSync()).thenReturn(syncSummary(true));
        when(recommendationSnapshotService.snapshotToday())
                .thenReturn(new RecommendationSnapshotService.SnapshotSummary(DAY, 12, 10, 2, 0));

        mockMvc.perform(post("/api/admin/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sync.success").value(true))
                .andExpect(jsonPath("$.snapshot.inserted").value(10));

        verify(recommendationService).evictAllCaches();
        verify(recommendationSnapshotService).snapshotToday();
    }

    @Test
    void failedSyncSkipsSnapshot() throws Exception {
        when(dataSyncService.runFullSync()).thenReturn(syncSummary(false));

        mockMvc.perform(post("/api/admin/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sync.success").value(false))
                .andExpect(jsonPath("$.snapshot").doesNotExist());

        verify(recommendationService).evictAllCaches();
        verify(recommendationSnapshotService, never()).snapshotToday();
    }

    @Test
    void seasonSyncOnlySyncsThatSeasonsFixtures() throws Exception {
        when(fixtureService.syncFixturesForSeason(1625L, "Manual"))
                .thenReturn(new FixtureService.SyncResult(3, 4, 10));
        when(recommendationSnapshotService.snapshotToday())
                .thenReturn(new RecommendationSnapshotService.SnapshotSummary(DAY, 0, 0, 0, 0));

        mockMvc.perform(post("/api/admin/sync").param("seasonId", "1625"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sync.fixturesProcessed").value(7));

        verify(dataSyncService, never()).runFullSync();
    }

    @Test
    void snapshotUsesGivenDate() throws Exception {
        when(recommendationSnapshotService.snapshotForDate(DAY))
                .thenReturn(new RecommendationSnapshotService.SnapshotSummary(DAY, 1, 1, 0, 0));

        mockMvc.perform(post("/api/admin/snapshot").param("date", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotDate").value("2026-10-10"));

        verify(recommendationSnapshotService, never()).snapshotToday();
    }

    @Test
    void resultsIngestThenSettles() throws Exception {
        when(resultsMatchIngestService.ingestPendingResults())
                .thenReturn(new ResultsMatchIngestService.IngestSummary(DAY, 2, 15, 1, Set.of(1L)));
        when(settlementService.settlePending())
                .thenReturn(new SettlementService.SettlementSummary(20, 15, 5, 0));

        mockMvc.perform(post("/api/admin/results-ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingest.matchesUpserted").value(15))
                .andExpect(jsonPath("$.settlement.resolved").value(15));
    }

    @Test
    void oddsDiagnosticsCountsCoverage() throws Exception {
        Fixture priced = Fixture.builder().id(1L).homeTeamName("A").awayTeamName("B").build();
        Fixture zeroed = Fixture.builder().id(2L).homeTeamName("C").awayTeamName("D").build();
        Fixture unpriced = Fixture.builder().id(3L).homeTeamName("E").awayTeamName("F").build();
        when(fixtureRepository.findByDateRangeAndStatus(anyLong(), anyLong(), anyString()))
                .thenReturn(List.of(priced, zeroed, unpriced));
        when(fixtureOddsRepository.findById(any())).thenReturn(Optional.empty());
        when(fixtureOddsRepository.findById(1L)).thenReturn(Optional.of(
                FixtureOdds.builder().fixtureId(1L).oddsFt1(1.9).oddsBttsYes(1.8).build()));
        when(fixtureOddsRepository.findById(2L)).thenReturn(Optional.of(
                FixtureOdds.builder().fixtureId(2L).oddsFt1(0.0).oddsBttsYes(0.0).build()));

        mockMvc.perform(get("/api/admin/diagnostics/odds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingFixturesCount").value(3))
                .andExpect(jsonPath("$.fixturesWithOddsRecord").value(2))
                .andExpect(jsonPath("$.fixturesWithBttsOdds").value(1))
                .andExpect(jsonPath("$.fixturesWithMatchOdds").value(1))
                .andExpect(jsonPath("$.fixturesWithZeroOdds").value(1));
    }
}
