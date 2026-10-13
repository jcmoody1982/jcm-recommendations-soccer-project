package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElitePicksServiceTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate DAY_ONE = LocalDate.of(2026, 10, 13);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 10, 14);

    @Mock
    private RecommendationService recommendationService;
    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    private ElitePicksService service;

    @BeforeEach
    void setUp() {
        ResultsProperties properties = new ResultsProperties();
        properties.setTimezone("Europe/London");
        service = new ElitePicksService(recommendationService, snapshotRepository, properties);
    }

    @Test
    void snapshottedDayUsesPersistedRanksAndLaterDayIsRankedLive() {
        when(recommendationService.generateAllRecommendations(7.0)).thenReturn(List.of(
                rec(1L, DAY_ONE, RecommendationType.BTTS, 90.0, 1.80),
                rec(2L, DAY_ONE, RecommendationType.MATCH_RESULT, 70.0, 1.90),
                rec(3L, DAY_TWO, RecommendationType.MATCH_RESULT, 75.0, 1.70),
                rec(4L, DAY_TWO, RecommendationType.BTTS, 80.0, 1.85),
                rec(5L, DAY_TWO, RecommendationType.BTTS, 85.0, 1.10)));
        when(snapshotRepository.findBySnapshotDateOrderByMatchDateUnixAscIdAsc(DAY_ONE)).thenReturn(List.of(
                snapshot(1L, "BTTS", null),
                snapshot(2L, "MATCH_RESULT", 1)));
        when(snapshotRepository.findBySnapshotDateOrderByMatchDateUnixAscIdAsc(DAY_TWO)).thenReturn(List.of());

        List<ElitePicksService.ElitePick> picks = service.elitePicks(7.0);

        // Day one keeps the frozen snapshot ranks even though BTTS scores higher live.
        // Day two is ranked live; the 1.10 price is below Elite's floor.
        assertThat(picks).containsExactly(
                new ElitePicksService.ElitePick(DAY_ONE, 1, 2L, "MATCH_RESULT"),
                new ElitePicksService.ElitePick(DAY_TWO, 1, 4L, "BTTS"),
                new ElitePicksService.ElitePick(DAY_TWO, 2, 3L, "MATCH_RESULT"));
    }

    private static Recommendation rec(Long fixtureId, LocalDate day, RecommendationType type, double score, Double odds) {
        return Recommendation.builder()
                .fixtureId(fixtureId)
                .matchDateUnix(day.atTime(15, 0).atZone(LONDON).toEpochSecond())
                .type(type)
                .confidence(ConfidenceLevel.STRONG)
                .score(score)
                .odds(odds)
                .market(type == RecommendationType.BTTS ? "BTTS Yes" : "Home Win")
                .build();
    }

    private static RecommendationSnapshot snapshot(Long fixtureId, String type, Integer eliteRank) {
        return RecommendationSnapshot.builder()
                .fixtureId(fixtureId)
                .snapshotDate(DAY_ONE)
                .type(type)
                .market("Home Win")
                .eliteRank(eliteRank)
                .build();
    }
}
