package com.jcm.recommendations.soccer.core.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationSnapshotServiceTest {

    @Mock
    private RecommendationService recommendationService;
    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    private ResultsProperties properties;
    private RecommendationSnapshotService service;

    @BeforeEach
    void setUp() {
        properties = new ResultsProperties();
        properties.setTimezone("Europe/London");
        service = new RecommendationSnapshotService(
                recommendationService, snapshotRepository, properties, new ObjectMapper());
    }

    @Test
    void snapshotsOnlyStrongAndModerateForTodayKickoffs() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
        long todayKickoff = today.atTime(15, 0).atZone(ZoneId.of("Europe/London")).toEpochSecond();
        long tomorrowKickoff = today.plusDays(1).atTime(15, 0).atZone(ZoneId.of("Europe/London")).toEpochSecond();

        when(recommendationService.generateAllRecommendations(anyDouble())).thenReturn(List.of(
                rec(1L, todayKickoff, ConfidenceLevel.STRONG, RecommendationType.BTTS),
                rec(2L, todayKickoff, ConfidenceLevel.WEAK, RecommendationType.BTTS),
                rec(3L, tomorrowKickoff, ConfidenceLevel.STRONG, RecommendationType.BTTS)
        ));
        when(snapshotRepository.findBySnapshotDateAndFixtureIdAndType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecommendationSnapshotService.SnapshotSummary summary = service.snapshotForDate(today);

        assertThat(summary.considered()).isEqualTo(1);
        assertThat(summary.inserted()).isEqualTo(1);
        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getFixtureId()).isEqualTo(1L);
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.PENDING);
    }

    @Test
    void doesNotOverwritePostKickoffPending() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
        long pastKickoff = today.atTime(10, 0).atZone(ZoneId.of("Europe/London")).toEpochSecond();
        if (pastKickoff > Instant.now().getEpochSecond()) {
            pastKickoff = Instant.now().getEpochSecond() - 60;
        }

        Recommendation existing = rec(1L, pastKickoff, ConfidenceLevel.STRONG, RecommendationType.BTTS);
        when(recommendationService.generateAllRecommendations(anyDouble())).thenReturn(List.of(existing));
        when(snapshotRepository.findBySnapshotDateAndFixtureIdAndType(today, 1L, "BTTS"))
                .thenReturn(Optional.of(RecommendationSnapshot.builder()
                        .id(99L)
                        .snapshotDate(today)
                        .fixtureId(1L)
                        .type("BTTS")
                        .matchDateUnix(pastKickoff)
                        .outcome(PickOutcome.PENDING)
                        .market("BTTS Yes")
                        .build()));

        RecommendationSnapshotService.SnapshotSummary summary = service.snapshotForDate(today);

        assertThat(summary.skipped()).isEqualTo(1);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void isKickoffOnDateUsesBrandTimezone() {
        LocalDate date = LocalDate.of(2026, 8, 2);
        long londonAfternoon = date.atTime(18, 0).atZone(ZoneId.of("Europe/London")).toEpochSecond();
        assertThat(service.isKickoffOnDate(londonAfternoon, date)).isTrue();
        assertThat(service.isKickoffOnDate(londonAfternoon, date.plusDays(1))).isFalse();
    }

    @Test
    void doesNotOverwriteSettledRows() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
        long todayKickoff = Instant.now().getEpochSecond() + 3600;
        LocalDate kickoffDate = Instant.ofEpochSecond(todayKickoff)
                .atZone(ZoneId.of("Europe/London")).toLocalDate();

        when(recommendationService.generateAllRecommendations(anyDouble()))
                .thenReturn(List.of(rec(1L, todayKickoff, ConfidenceLevel.STRONG, RecommendationType.BTTS)));
        when(snapshotRepository.findBySnapshotDateAndFixtureIdAndType(kickoffDate, 1L, "BTTS"))
                .thenReturn(Optional.of(RecommendationSnapshot.builder()
                        .id(5L)
                        .snapshotDate(kickoffDate)
                        .fixtureId(1L)
                        .type("BTTS")
                        .matchDateUnix(todayKickoff)
                        .outcome(PickOutcome.WIN)
                        .market("BTTS Yes")
                        .build()));

        RecommendationSnapshotService.SnapshotSummary summary = service.snapshotForDate(kickoffDate);

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.updated()).isEqualTo(0);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void updatesPreKickoffPendingRow() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
        long futureKickoff = Instant.now().getEpochSecond() + 7200;
        LocalDate kickoffDate = Instant.ofEpochSecond(futureKickoff)
                .atZone(ZoneId.of("Europe/London")).toLocalDate();

        Recommendation updated = rec(1L, futureKickoff, ConfidenceLevel.MODERATE, RecommendationType.BTTS);
        updated.setMarket("BTTS Yes");
        updated.setScore(81.0);
        updated.setFactors(java.util.Map.of("edge", 0.12));

        when(recommendationService.generateAllRecommendations(anyDouble())).thenReturn(List.of(updated));
        when(snapshotRepository.findBySnapshotDateAndFixtureIdAndType(kickoffDate, 1L, "BTTS"))
                .thenReturn(Optional.of(RecommendationSnapshot.builder()
                        .id(7L)
                        .snapshotDate(kickoffDate)
                        .fixtureId(1L)
                        .type("BTTS")
                        .matchDateUnix(futureKickoff)
                        .outcome(PickOutcome.PENDING)
                        .market("BTTS Yes")
                        .score(60.0)
                        .build()));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecommendationSnapshotService.SnapshotSummary summary = service.snapshotForDate(kickoffDate);

        assertThat(summary.updated()).isEqualTo(1);
        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualTo(81.0);
        assertThat(captor.getValue().getConfidence()).isEqualTo("MODERATE");
        assertThat(captor.getValue().getFactorsJson()).contains("edge");
        assertThat(captor.getValue().getOutcome()).isEqualTo(PickOutcome.PENDING);
    }

    @Test
    void persistsFactorsJsonOnInsert() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/London"));
        long todayKickoff = today.atTime(20, 0).atZone(ZoneId.of("Europe/London")).toEpochSecond();
        if (todayKickoff <= Instant.now().getEpochSecond()) {
            todayKickoff = Instant.now().getEpochSecond() + 1800;
            today = Instant.ofEpochSecond(todayKickoff).atZone(ZoneId.of("Europe/London")).toLocalDate();
        }

        Recommendation recommendation = rec(9L, todayKickoff, ConfidenceLevel.STRONG, RecommendationType.OVER_GOALS);
        recommendation.setMarket("Over 2.5 Goals");
        recommendation.setFactors(java.util.Map.of("expectedGoals", 2.8));

        when(recommendationService.generateAllRecommendations(anyDouble())).thenReturn(List.of(recommendation));
        when(snapshotRepository.findBySnapshotDateAndFixtureIdAndType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.snapshotForDate(today);

        ArgumentCaptor<RecommendationSnapshot> captor = ArgumentCaptor.forClass(RecommendationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getFactorsJson()).contains("expectedGoals");
        assertThat(captor.getValue().getType()).isEqualTo("OVER_GOALS");
    }

    private static Recommendation rec(Long fixtureId, long kickoff, ConfidenceLevel confidence, RecommendationType type) {
        return Recommendation.builder()
                .fixtureId(fixtureId)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .homeTeamName("Home")
                .awayTeamName("Away")
                .matchDateUnix(kickoff)
                .leagueId(10L)
                .leagueName("League")
                .type(type)
                .confidence(confidence)
                .score(70.0)
                .market("BTTS Yes")
                .generatedAt(Instant.now())
                .build();
    }
}
