package com.jcm.recommendations.soccer.core.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.core.results.settlement.PickSettlementGrader;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;
    @Mock
    private CompletedMatchRepository completedMatchRepository;

    private ResultsProperties properties;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        properties = new ResultsProperties();
        properties.setTimezone("Europe/London");
        properties.setPendingLookbackDays(7);
        service = new SettlementService(
                snapshotRepository,
                completedMatchRepository,
                new PickSettlementGrader(),
                properties,
                new ObjectMapper());
    }

    @Test
    void voidsCanceledMatches() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "BTTS", "BTTS Yes");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.of(
                CompletedMatch.builder().fixtureId(1L).status("canceled").homeGoals(0).awayGoals(0).build()));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.resolved()).isEqualTo(1);
        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.VOID);
    }

    @Test
    void marksCornersUnsupported() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "OVER_CORNERS", "Over 9.5 Corners");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.resolved()).isEqualTo(1);
        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.UNSUPPORTED);
    }

    @Test
    void expiresPendingOutsideLookback() {
        LocalDate old = LocalDate.now(properties.zoneId()).minusDays(10);
        RecommendationSnapshot snap = pending(old, "BTTS", "BTTS Yes");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.expiredVoids()).isEqualTo(1);
        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.VOID);
    }

    @Test
    void settlesCompleteMatchToWinAndStoresMatchResultJson() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "BTTS", "BTTS Yes");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.of(
                CompletedMatch.builder()
                        .fixtureId(1L)
                        .status("complete")
                        .homeGoals(2)
                        .awayGoals(1)
                        .htHomeGoals(1)
                        .htAwayGoals(0)
                        .build()));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.resolved()).isEqualTo(1);
        assertThat(summary.stillPending()).isEqualTo(0);
        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.WIN);
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
        assertThat(captor.getValue().getMatchResultJson()).contains("\"homeGoals\":2");
        assertThat(captor.getValue().getMatchResultJson()).contains("\"awayGoals\":1");
    }

    @Test
    void settlesCompleteMatchToLoss() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "OVER_GOALS", "Over 2.5 Goals");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.of(
                CompletedMatch.builder().fixtureId(1L).status("complete").homeGoals(1).awayGoals(0).build()));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.settlePending();

        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void leavesIncompleteMatchesPending() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "BTTS", "BTTS Yes");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.of(
                CompletedMatch.builder().fixtureId(1L).status("incomplete").build()));

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.stillPending()).isEqualTo(1);
        assertThat(summary.resolved()).isEqualTo(0);
        verify(snapshotRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void leavesMissingCompletedMatchPendingWithinLookback() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "DRAW", "Draw");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.empty());

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.stillPending()).isEqualTo(1);
        verify(snapshotRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void halfGoalMissingHtStaysPending() {
        LocalDate today = LocalDate.now(properties.zoneId());
        RecommendationSnapshot snap = pending(today, "FIRST_HALF_GOALS", "Over 0.5 HT Goals");
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of(snap));
        when(completedMatchRepository.findById(1L)).thenReturn(Optional.of(
                CompletedMatch.builder().fixtureId(1L).status("complete").homeGoals(2).awayGoals(1).build()));

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.stillPending()).isEqualTo(1);
        verify(snapshotRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void onlyPendingRowsAreLoadedForSettlement() {
        when(snapshotRepository.findByOutcome(PickOutcome.PENDING)).thenReturn(List.of());

        SettlementService.SettlementSummary summary = service.settlePending();

        assertThat(summary.pendingExamined()).isEqualTo(0);
        verify(snapshotRepository).findByOutcome(PickOutcome.PENDING);
        verify(snapshotRepository, org.mockito.Mockito.never()).save(any());
    }

    private static RecommendationSnapshot pending(LocalDate date, String type, String market) {
        return RecommendationSnapshot.builder()
                .id(10L)
                .snapshotDate(date)
                .fixtureId(1L)
                .type(type)
                .market(market)
                .homeTeamName("Arsenal")
                .awayTeamName("Chelsea")
                .outcome(PickOutcome.PENDING)
                .build();
    }
}
