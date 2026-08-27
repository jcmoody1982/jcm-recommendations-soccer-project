package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultsPerformanceServiceTest {

    @Mock
    private RecommendationSnapshotRepository snapshotRepository;

    private ResultsPerformanceService service;

    @BeforeEach
    void setUp() {
        ResultsProperties properties = new ResultsProperties();
        properties.setTimezone("Europe/London");
        service = new ResultsPerformanceService(snapshotRepository, properties);
    }

    @Test
    void aggregatesOverallConfidenceAndType() {
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        when(snapshotRepository.findBySnapshotDateBetweenInclusive(any(), any())).thenReturn(List.of(
                snap(1L, d1, 100L, "BTTS", "STRONG", PickOutcome.WIN, 1, 90.0, 1.80),
                snap(2L, d1, 200L, "BTTS", "MODERATE", PickOutcome.LOSS, null, 70.0, 1.90),
                snap(3L, d2, 300L, "OVER_GOALS", "STRONG", PickOutcome.WIN, 1, 88.0, 1.70),
                snap(4L, d2, 400L, "OVER_GOALS", "MODERATE", PickOutcome.VOID, null, 65.0, 2.00),
                snap(5L, d2, 500L, "DRAW", "MODERATE", PickOutcome.PENDING, null, 60.0, 3.20)
        ));

        ResultsPerformanceService.PerformanceView view = service.getPerformance("30d");

        assertThat(view.period()).isEqualTo("30d");
        assertThat(view.minSample()).isEqualTo(10);
        assertThat(view.overall().wins()).isEqualTo(2);
        assertThat(view.overall().losses()).isEqualTo(1);
        assertThat(view.overall().voids()).isEqualTo(1);
        assertThat(view.overall().pending()).isEqualTo(1);
        assertThat(view.overall().sampleSize()).isEqualTo(3);
        assertThat(view.overall().hitRate()).isEqualTo(2 * 100.0 / 3);
        assertThat(view.overall().enoughData()).isFalse();

        assertThat(view.elite().wins()).isEqualTo(2);
        assertThat(view.elite().losses()).isEqualTo(0);
        assertThat(view.elite().sampleSize()).isEqualTo(2);
        assertThat(view.elite().hitRate()).isEqualTo(100.0);

        assertThat(view.byConfidence().get("STRONG").wins()).isEqualTo(2);
        assertThat(view.byConfidence().get("STRONG").losses()).isEqualTo(0);
        assertThat(view.byConfidence().get("MODERATE").wins()).isEqualTo(0);
        assertThat(view.byConfidence().get("MODERATE").losses()).isEqualTo(1);

        assertThat(view.byType()).hasSize(RecommendationType.values().length - 1);
        assertThat(view.byType()).noneMatch(t -> "CLEAN_SHEET".equals(t.type()));
        ResultsPerformanceService.TypePerformance btts = view.byType().stream()
                .filter(t -> "BTTS".equals(t.type()))
                .findFirst()
                .orElseThrow();
        assertThat(btts.overall().wins()).isEqualTo(1);
        assertThat(btts.overall().losses()).isEqualTo(1);
        assertThat(btts.overall().hitRate()).isEqualTo(50.0);
        assertThat(btts.byConfidence().get("ELITE").wins()).isEqualTo(1);
        assertThat(btts.byConfidence().get("ELITE").sampleSize()).isEqualTo(1);

        ResultsPerformanceService.TypePerformance over15 = view.byType().stream()
                .filter(t -> "OVER_15_GOALS".equals(t.type()))
                .findFirst()
                .orElseThrow();
        assertThat(over15.overall().sampleSize()).isEqualTo(0);
        ResultsPerformanceService.TypePerformance over25 = view.byType().stream()
                .filter(t -> "OVER_25_GOALS".equals(t.type()))
                .findFirst()
                .orElseThrow();
        assertThat(over25.overall().sampleSize()).isEqualTo(0);
    }

    @Test
    void computesEliteOnReadWhenRanksMissing() {
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        when(snapshotRepository.findBySnapshotDateBetweenInclusive(any(), any())).thenReturn(List.of(
                snap(1L, d1, 100L, "BTTS", "STRONG", PickOutcome.WIN, null, 95.0, 1.70),
                snap(2L, d1, 200L, "MATCH_RESULT", "STRONG", PickOutcome.LOSS, null, 90.0, 1.80),
                snap(3L, d1, 300L, "DRAW", "MODERATE", PickOutcome.WIN, null, 80.0, 3.00)
        ));

        ResultsPerformanceService.PerformanceView view = service.getPerformance("30d");

        assertThat(view.elite().wins()).isEqualTo(1);
        assertThat(view.elite().losses()).isEqualTo(1);
        assertThat(view.elite().sampleSize()).isEqualTo(2);
        assertThat(view.elite().hitRate()).isEqualTo(50.0);
    }

    @Test
    void allPeriodUsesLessThanEqualQuery() {
        when(snapshotRepository.findBySnapshotDateLessThanEqual(any())).thenReturn(List.of(
                snap(1L, LocalDate.of(2026, 1, 1), 100L, "BTTS", "STRONG", PickOutcome.WIN, 1, 90.0, 1.80)
        ));

        ResultsPerformanceService.PerformanceView view = service.getPerformance("all");

        assertThat(view.period()).isEqualTo("all");
        assertThat(view.fromDate()).isNull();
        assertThat(view.overall().wins()).isEqualTo(1);
        assertThat(view.overall().sampleSize()).isEqualTo(1);
        assertThat(view.elite().wins()).isEqualTo(1);
    }

    private static RecommendationSnapshot snap(
            Long id,
            LocalDate date,
            Long fixtureId,
            String type,
            String confidence,
            PickOutcome outcome,
            Integer eliteRank,
            Double score,
            Double odds) {
        return RecommendationSnapshot.builder()
                .id(id)
                .snapshotDate(date)
                .fixtureId(fixtureId)
                .type(type)
                .confidence(confidence)
                .outcome(outcome)
                .eliteRank(eliteRank)
                .score(score)
                .odds(odds)
                .matchDateUnix(date.toEpochDay() * 86_400L)
                .build();
    }
}
