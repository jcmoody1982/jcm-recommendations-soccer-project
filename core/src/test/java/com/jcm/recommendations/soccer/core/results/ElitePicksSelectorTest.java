package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElitePicksSelectorTest {

    @Test
    void selectsStrongEligibleOnlyDedupesFixtureAndCapsAtTen() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        List<RecommendationSnapshot> day = List.of(
                snap(1L, date, 100L, "BTTS", "STRONG", 90.0, 1.8, 1000L),
                snap(2L, date, 100L, "MATCH_RESULT", "STRONG", 95.0, 2.0, 1000L),
                snap(3L, date, 200L, "DRAW", "MODERATE", 99.0, 1.5, 2000L),
                snap(4L, date, 300L, "BOOKING_POINTS", "STRONG", 99.0, 1.5, 3000L),
                snap(14L, date, 1300L, "PLAYER_TO_SCORE", "STRONG", 99.0, 1.4, 13000L),
                snap(5L, date, 400L, "OVER_GOALS", "STRONG", 80.0, 1.9, 4000L),
                snap(6L, date, 500L, "UNDER_GOALS", "STRONG", 79.0, 2.1, 5000L),
                snap(7L, date, 600L, "CLEAN_SHEET", "STRONG", 78.0, 2.2, 6000L),
                snap(8L, date, 700L, "RESULT_BTTS", "STRONG", 77.0, 2.3, 7000L),
                snap(9L, date, 800L, "TOP_VS_BOTTOM", "STRONG", 76.0, 2.4, 8000L),
                snap(10L, date, 900L, "FIRST_HALF_GOALS", "STRONG", 75.0, 2.5, 9000L),
                snap(11L, date, 1000L, "SECOND_HALF_GOALS", "STRONG", 74.0, 2.6, 10_000L),
                snap(12L, date, 1100L, "VALUE_BET", "STRONG", 73.0, 2.7, 11_000L),
                snap(13L, date, 1200L, "DOUBLE_CHANCE", "STRONG", 72.0, 2.8, 12_000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite).hasSize(9);
        assertThat(elite.getFirst().getFixtureId()).isEqualTo(100L);
        assertThat(elite.getFirst().getType()).isEqualTo("MATCH_RESULT");
        assertThat(elite.stream().map(RecommendationSnapshot::getFixtureId).distinct().count()).isEqualTo(9);
        assertThat(elite).noneMatch(r -> "BOOKING_POINTS".equals(r.getType()));
        assertThat(elite).noneMatch(r -> "PLAYER_TO_SCORE".equals(r.getType()));
        assertThat(elite).noneMatch(r -> "CLEAN_SHEET".equals(r.getType()));
        assertThat(elite).noneMatch(r -> "MODERATE".equalsIgnoreCase(r.getConfidence()));
        assertThat(elite.getLast().getFixtureId()).isEqualTo(1200L);
    }

    @Test
    void prefersLowerOddsThenSoonerKickoffOnScoreTie() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        List<RecommendationSnapshot> day = List.of(
                snap(1L, date, 100L, "BTTS", "STRONG", 80.0, 2.5, 3000L),
                snap(2L, date, 200L, "BTTS", "STRONG", 80.0, 1.9, 4000L),
                snap(3L, date, 300L, "BTTS", "STRONG", 80.0, 1.9, 2000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite).extracting(RecommendationSnapshot::getFixtureId)
                .containsExactly(300L, 200L, 100L);
    }

    @Test
    void capsBttsAtThreeAndFillsWithOtherMarkets() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        List<RecommendationSnapshot> day = List.of(
                snap(1L, date, 100L, "BTTS", "STRONG", 95.0, 1.8, 1000L),
                snap(2L, date, 200L, "BTTS", "STRONG", 94.0, 1.8, 2000L),
                snap(3L, date, 300L, "BTTS", "STRONG", 93.0, 1.8, 3000L),
                snap(4L, date, 400L, "BTTS", "STRONG", 92.0, 1.8, 4000L),
                snap(5L, date, 500L, "BTTS", "STRONG", 91.0, 1.8, 5000L),
                snap(6L, date, 600L, "OVER_GOALS", "STRONG", 70.0, 2.0, 6000L),
                snap(7L, date, 700L, "DRAW", "STRONG", 69.0, 2.1, 7000L),
                snap(8L, date, 400L, "MATCH_RESULT", "STRONG", 68.0, 2.2, 4000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite.stream().filter(r -> "BTTS".equals(r.getType())).count()).isEqualTo(3);
        assertThat(elite).extracting(RecommendationSnapshot::getType)
                .containsExactly("BTTS", "BTTS", "BTTS", "OVER_GOALS", "DRAW", "MATCH_RESULT");
        assertThat(elite).extracting(RecommendationSnapshot::getFixtureId)
                .containsExactly(100L, 200L, 300L, 600L, 700L, 400L);
    }

    private static RecommendationSnapshot snap(
            Long id, LocalDate date, Long fixtureId, String type, String confidence,
            double score, double odds, long kickoff) {
        return RecommendationSnapshot.builder()
                .id(id)
                .snapshotDate(date)
                .fixtureId(fixtureId)
                .type(type)
                .market(type)
                .confidence(confidence)
                .score(score)
                .odds(odds)
                .matchDateUnix(kickoff)
                .outcome(PickOutcome.PENDING)
                .build();
    }
}
