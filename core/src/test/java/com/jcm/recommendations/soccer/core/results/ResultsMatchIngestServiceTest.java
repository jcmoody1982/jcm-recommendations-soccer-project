package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.GoalDetailDto;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsMatchIngestServiceTest {

    @Mock
    private FootyStatsApiClient apiClient;
    @Mock
    private CompletedMatchRepository completedMatchRepository;
    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    private ResultsProperties properties;
    private ResultsMatchIngestService service;

    @BeforeEach
    void setUp() {
        properties = new ResultsProperties();
        properties.setTimezone("Europe/London");
        properties.setPendingLookbackDays(7);
        service = new ResultsMatchIngestService(
                apiClient, completedMatchRepository, snapshotRepository, properties);
        lenient().when(snapshotRepository.findDistinctFixtureIdsByTypesAndOutcomesOnDate(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(snapshotRepository.findDistinctFixtureIdsByTypesAndOutcomesSince(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void ingestPendingFetchesYesterdayAndExtraPendingDates() {
        LocalDate today = LocalDate.now(properties.zoneId());
        LocalDate yesterday = today.minusDays(1);
        LocalDate olderPending = today.minusDays(2);
        LocalDate lookbackStart = today.minusDays(7);

        when(snapshotRepository.findDistinctSnapshotDatesByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart))
                .thenReturn(List.of(olderPending));
        when(apiClient.fetchTodaysMatches(eq(yesterday.toString()), eq("Europe/London")))
                .thenReturn(List.of(match(100L, "complete", 2, 1)));
        when(apiClient.fetchTodaysMatches(eq(olderPending.toString()), eq("Europe/London")))
                .thenReturn(List.of());
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart))
                .thenReturn(List.of(100L));
        when(completedMatchRepository.findById(100L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(CompletedMatch.builder().fixtureId(100L).status("complete").build()));
        when(completedMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultsMatchIngestService.IngestSummary summary = service.ingestPendingResults();

        assertThat(summary.primaryDate()).isEqualTo(yesterday);
        assertThat(summary.datesProcessed()).isEqualTo(2);
        assertThat(summary.touchedFixtureIds()).contains(100L);
        verify(apiClient).fetchTodaysMatches(yesterday.toString(), "Europe/London");
        verify(apiClient).fetchTodaysMatches(olderPending.toString(), "Europe/London");
        verify(apiClient, never()).fetchMatch(100L);
    }

    @Test
    void upsertMapsGoalsAndTreatsSentinelsAsNull() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        MatchDto dto = match(55L, "Complete", 3, 0);
        dto.setHtGoalsTeamA(1);
        dto.setHtGoalsTeamB(-1);
        dto.setGoals2hgTeamA(2);
        dto.setGoals2hgTeamB(-2);
        dto.setTeamACorners(5);
        dto.setTeamBCorners(-1);

        when(apiClient.fetchTodaysMatches("2026-08-01", "Europe/London")).thenReturn(List.of(dto));
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDate(PickOutcome.PENDING, date))
                .thenReturn(List.of(55L));
        when(completedMatchRepository.findById(55L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(CompletedMatch.builder().fixtureId(55L).status("complete").build()));
        when(completedMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultsMatchIngestService.IngestSummary summary = service.ingestForDate(date);

        assertThat(summary.matchesUpserted()).isEqualTo(1);
        assertThat(summary.fallbackFetches()).isEqualTo(0);
        ArgumentCaptor<CompletedMatch> captor = ArgumentCaptor.forClass(CompletedMatch.class);
        verify(completedMatchRepository).save(captor.capture());
        CompletedMatch saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("complete");
        assertThat(saved.getHomeGoals()).isEqualTo(3);
        assertThat(saved.getAwayGoals()).isEqualTo(0);
        assertThat(saved.getHtHomeGoals()).isEqualTo(1);
        assertThat(saved.getHtAwayGoals()).isNull();
        assertThat(saved.getSecondHalfHomeGoals()).isEqualTo(2);
        assertThat(saved.getSecondHalfAwayGoals()).isNull();
        assertThat(saved.getHomeCorners()).isEqualTo(5);
        assertThat(saved.getAwayCorners()).isNull();
        assertThat(saved.getSourceDate()).isEqualTo(date);
        assertThat(saved.getFetchedAt()).isNotNull();
        verify(apiClient, never()).fetchMatch(55L);
    }

    @Test
    void fallbackFetchesMatchForPlayerPropWhenGoalEventsMissing() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        when(apiClient.fetchTodaysMatches("2026-08-03", "Europe/London"))
                .thenReturn(List.of(match(100L, "complete", 2, 0)));
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDate(PickOutcome.PENDING, date))
                .thenReturn(List.of(100L));
        when(snapshotRepository.findDistinctFixtureIdsByTypesAndOutcomesOnDate(any(), any(), eq(date)))
                .thenReturn(List.of(100L));
        when(completedMatchRepository.findById(100L)).thenAnswer(inv -> Optional.of(
                CompletedMatch.builder().fixtureId(100L).status("complete").homeGoals(2).awayGoals(0).build()));
        MatchDto detailed = match(100L, "complete", 2, 0);
        GoalDetailDto goal = new GoalDetailDto();
        goal.setPlayerId(8298L);
        goal.setAssistPlayerId(4281L);
        goal.setTime("17");
        detailed.setTeamAGoalDetails(List.of(goal));
        detailed.setTeamBGoalDetails(List.of());
        when(apiClient.fetchMatch(100L)).thenReturn(detailed);
        when(completedMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultsMatchIngestService.IngestSummary summary = service.ingestForDate(date);

        assertThat(summary.fallbackFetches()).isEqualTo(1);
        verify(apiClient).fetchMatch(100L);
        ArgumentCaptor<CompletedMatch> captor = ArgumentCaptor.forClass(CompletedMatch.class);
        verify(completedMatchRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getGoalEventsJson()).contains("8298").contains("4281");
    }

    @Test
    void ingestForDateFallsBackToMatchWhenMissingFromTodaysMatches() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        when(apiClient.fetchTodaysMatches("2026-08-03", "Europe/London"))
                .thenReturn(List.of(match(100L, "complete", 2, 0)));
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDate(PickOutcome.PENDING, date))
                .thenReturn(List.of(100L, 8468273L));
        when(completedMatchRepository.findById(100L))
                .thenReturn(Optional.of(CompletedMatch.builder().fixtureId(100L).status("complete").build()));
        when(completedMatchRepository.findById(8468273L)).thenReturn(Optional.empty());
        when(apiClient.fetchMatch(8468273L)).thenReturn(match(8468273L, "complete", 1, 1));
        when(completedMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultsMatchIngestService.IngestSummary summary = service.ingestForDate(date);

        assertThat(summary.matchesUpserted()).isEqualTo(2);
        assertThat(summary.fallbackFetches()).isEqualTo(1);
        assertThat(summary.touchedFixtureIds()).contains(100L, 8468273L);
        verify(apiClient).fetchMatch(8468273L);
        verify(apiClient, never()).fetchMatch(100L);
    }

    @Test
    void fallbackFetchesMatchWhenPendingAndNotComplete() {
        LocalDate today = LocalDate.now(properties.zoneId());
        LocalDate yesterday = today.minusDays(1);
        LocalDate lookbackStart = today.minusDays(7);

        when(snapshotRepository.findDistinctSnapshotDatesByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart))
                .thenReturn(List.of());
        when(apiClient.fetchTodaysMatches(eq(yesterday.toString()), eq("Europe/London")))
                .thenReturn(List.of());
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart))
                .thenReturn(List.of(77L));
        when(completedMatchRepository.findById(77L))
                .thenReturn(Optional.of(CompletedMatch.builder().fixtureId(77L).status("incomplete").build()));
        when(apiClient.fetchMatch(77L)).thenReturn(match(77L, "complete", 1, 1));
        when(completedMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultsMatchIngestService.IngestSummary summary = service.ingestPendingResults();

        assertThat(summary.fallbackFetches()).isEqualTo(1);
        assertThat(summary.touchedFixtureIds()).contains(77L);
        verify(apiClient).fetchMatch(77L);
        ArgumentCaptor<CompletedMatch> captor = ArgumentCaptor.forClass(CompletedMatch.class);
        verify(completedMatchRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("complete");
        assertThat(captor.getValue().getHomeGoals()).isEqualTo(1);
    }

    @Test
    void fallbackFetchesWhenNoCompletedMatchRow() {
        LocalDate today = LocalDate.now(properties.zoneId());
        LocalDate yesterday = today.minusDays(1);
        LocalDate lookbackStart = today.minusDays(7);

        when(snapshotRepository.findDistinctSnapshotDatesByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart))
                .thenReturn(List.of());
        when(apiClient.fetchTodaysMatches(anyString(), anyString())).thenReturn(List.of());
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart))
                .thenReturn(List.of(88L));
        when(completedMatchRepository.findById(88L)).thenReturn(Optional.empty());
        when(apiClient.fetchMatch(88L)).thenReturn(match(88L, "suspended", null, null));
        when(completedMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResultsMatchIngestService.IngestSummary summary = service.ingestPendingResults();

        assertThat(summary.fallbackFetches()).isEqualTo(1);
        ArgumentCaptor<CompletedMatch> captor = ArgumentCaptor.forClass(CompletedMatch.class);
        verify(completedMatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("suspended");
        assertThat(captor.getValue().getHomeGoals()).isNull();
    }

    @Test
    void skipsMatchesWithoutId() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        MatchDto missingId = new MatchDto();
        missingId.setStatus("complete");
        when(apiClient.fetchTodaysMatches("2026-08-01", "Europe/London")).thenReturn(List.of(missingId));
        when(snapshotRepository.findDistinctFixtureIdsByOutcomeAndSnapshotDate(PickOutcome.PENDING, date))
                .thenReturn(List.of());

        ResultsMatchIngestService.IngestSummary summary = service.ingestForDate(date);

        assertThat(summary.matchesUpserted()).isEqualTo(0);
        verify(completedMatchRepository, never()).save(any());
    }

    private static MatchDto match(Long id, String status, Integer home, Integer away) {
        MatchDto dto = new MatchDto();
        dto.setId(id);
        dto.setStatus(status);
        dto.setHomeGoalCount(home);
        dto.setAwayGoalCount(away);
        return dto;
    }
}
