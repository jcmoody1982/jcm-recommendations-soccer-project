package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsQueryServiceTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;
    @Mock
    private CompletedMatchRepository completedMatchRepository;

    private ResultsQueryService service;

    @BeforeEach
    void setUp() {
        ResultsProperties properties = new ResultsProperties();
        properties.setTimezone("Europe/London");
        service = new ResultsQueryService(snapshotRepository, completedMatchRepository, properties);
    }

    @Test
    void groupsByFixtureAndComputesHitRate() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(snapshotRepository.findBySnapshotDateOrderByMatchDateUnixAscIdAsc(date)).thenReturn(List.of(
                snap(1L, date, 100L, "BTTS", "STRONG", PickOutcome.WIN, 1000L),
                snap(2L, date, 100L, "OVER_GOALS", "MODERATE", PickOutcome.LOSS, 1000L),
                snap(3L, date, 200L, "DRAW", "STRONG", PickOutcome.PENDING, 2000L)
        ));
        when(completedMatchRepository.findByFixtureIdIn(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of(CompletedMatch.builder()
                        .fixtureId(100L).status("complete").homeGoals(2).awayGoals(1).build()));

        ResultsQueryService.DayResultsView view = service.getDayResults(date, null);

        assertThat(view.snapshotDate()).isEqualTo(date);
        assertThat(view.summary().wins()).isEqualTo(1);
        assertThat(view.summary().losses()).isEqualTo(1);
        assertThat(view.summary().pending()).isEqualTo(1);
        assertThat(view.summary().hitRate()).isEqualTo(50.0);
        assertThat(view.strongSummary().wins()).isEqualTo(1);
        assertThat(view.strongSummary().losses()).isEqualTo(0);
        assertThat(view.strongSummary().pending()).isEqualTo(1);
        assertThat(view.strongSummary().hitRate()).isEqualTo(100.0);
        assertThat(view.moderateSummary().wins()).isEqualTo(0);
        assertThat(view.moderateSummary().losses()).isEqualTo(1);
        assertThat(view.moderateSummary().hitRate()).isEqualTo(0.0);
        assertThat(view.eliteSummary().wins()).isEqualTo(1);
        assertThat(view.eliteSummary().pending()).isEqualTo(1);
        assertThat(view.eliteSummary().hitRate()).isEqualTo(100.0);
        assertThat(view.eliteFixtures()).hasSize(2);
        assertThat(view.eliteFixtures().getFirst().picks().getFirst().eliteRank()).isEqualTo(1);
        assertThat(view.eliteFixtures().getFirst().picks().getFirst().type()).isEqualTo("BTTS");
        assertThat(view.eliteFixtures().get(1).picks().getFirst().type()).isEqualTo("DRAW");
        assertThat(view.fixtures()).hasSize(2);
        assertThat(view.fixtures().getFirst().fixtureId()).isEqualTo(100L);
        assertThat(view.fixtures().getFirst().scoreline().home()).isEqualTo(2);
        assertThat(view.fixtures().getFirst().picks()).hasSize(2);
        assertThat(view.fixtures().getFirst().picks().getFirst().confidence()).isEqualTo("STRONG");
    }

    @Test
    void prefersPersistedEliteRanksOverOnTheFlySelection() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(snapshotRepository.findBySnapshotDateOrderByMatchDateUnixAscIdAsc(date)).thenReturn(List.of(
                snap(1L, date, 100L, "BTTS", "STRONG", PickOutcome.WIN, 1000L, 90.0, 2),
                snap(2L, date, 200L, "DRAW", "STRONG", PickOutcome.LOSS, 2000L, 70.0, 1)
        ));
        when(completedMatchRepository.findByFixtureIdIn(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of());

        ResultsQueryService.DayResultsView view = service.getDayResults(date, null);

        assertThat(view.eliteFixtures()).extracting(ResultsQueryService.FixtureResultsView::fixtureId)
                .containsExactly(200L, 100L);
        assertThat(view.eliteFixtures().getFirst().picks().getFirst().eliteRank()).isEqualTo(1);
    }

    @Test
    void filtersByOutcome() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(snapshotRepository.findBySnapshotDateOrderByMatchDateUnixAscIdAsc(date)).thenReturn(List.of(
                snap(1L, date, 100L, "BTTS", "STRONG", PickOutcome.WIN, 1000L),
                snap(2L, date, 100L, "DRAW", "MODERATE", PickOutcome.LOSS, 1000L)
        ));
        when(completedMatchRepository.findByFixtureIdIn(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of());

        ResultsQueryService.DayResultsView view = service.getDayResults(date, "WIN");

        assertThat(view.fixtures()).hasSize(1);
        assertThat(view.fixtures().getFirst().picks()).hasSize(1);
        assertThat(view.fixtures().getFirst().picks().getFirst().outcome()).isEqualTo("WIN");
        // Summary is always for the full day
        assertThat(view.summary().wins()).isEqualTo(1);
        assertThat(view.summary().losses()).isEqualTo(1);
    }

    private static RecommendationSnapshot snap(
            Long id, LocalDate date, Long fixtureId, String type, String confidence,
            PickOutcome outcome, long kickoff) {
        return snap(id, date, fixtureId, type, confidence, outcome, kickoff, 70.0, null);
    }

    private static RecommendationSnapshot snap(
            Long id, LocalDate date, Long fixtureId, String type, String confidence,
            PickOutcome outcome, long kickoff, double score, Integer eliteRank) {
        return RecommendationSnapshot.builder()
                .id(id)
                .snapshotDate(date)
                .fixtureId(fixtureId)
                .homeTeamName("Home")
                .awayTeamName("Away")
                .leagueName("League")
                .type(type)
                .market(type)
                .confidence(confidence)
                .score(score)
                .odds(1.9)
                .matchDateUnix(kickoff)
                .outcome(outcome)
                .eliteRank(eliteRank)
                .build();
    }
}
