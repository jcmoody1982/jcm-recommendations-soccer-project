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
    void excludesOverHalfGoalsMarketsUntilScoringIsFixed() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        List<RecommendationSnapshot> day = List.of(
                snapWithMarket(1L, date, 100L, "SECOND_HALF_GOALS", "Over 0.5 2H Goals", "STRONG", 100.0, 1.5, 1000L),
                snapWithMarket(2L, date, 200L, "FIRST_HALF_GOALS", "Over 0.5 HT Goals", "STRONG", 100.0, 1.5, 2000L),
                snapWithMarket(3L, date, 300L, "SECOND_HALF_GOALS", "Over 1.5 2H Goals", "STRONG", 85.0, 2.0, 3000L),
                snapWithMarket(4L, date, 400L, "BTTS", "BTTS Yes", "STRONG", 80.0, 1.8, 4000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite).extracting(RecommendationSnapshot::getFixtureId)
                .containsExactly(300L, 400L);
        assertThat(elite).noneMatch(r -> ElitePicksSelector.isExcludedFromElitePicks(r.getMarket()));
    }

    @Test
    void capsAnySingleMarketAtThreeSoItCannotFillTheBoard() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        // Over 1.5 clears in roughly three quarters of fixtures, so on probability alone it
        // outranks everything else and used to take every slot.
        List<RecommendationSnapshot> day = List.of(
                snap(1L, date, 100L, "OVER_15_GOALS", "STRONG", 93.0, 1.22, 1000L),
                snap(2L, date, 200L, "OVER_15_GOALS", "STRONG", 92.0, 1.20, 2000L),
                snap(3L, date, 300L, "OVER_15_GOALS", "STRONG", 91.0, 1.24, 3000L),
                snap(4L, date, 400L, "OVER_15_GOALS", "STRONG", 90.0, 1.21, 4000L),
                snap(5L, date, 500L, "OVER_15_GOALS", "STRONG", 89.0, 1.23, 5000L),
                snap(6L, date, 600L, "MATCH_RESULT", "STRONG", 74.0, 1.90, 6000L),
                snap(7L, date, 700L, "DRAW", "STRONG", 71.0, 3.40, 7000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite.stream().filter(r -> "OVER_15_GOALS".equals(r.getType())).count()).isEqualTo(3);
        assertThat(elite).extracting(RecommendationSnapshot::getType)
                .containsExactly("OVER_15_GOALS", "OVER_15_GOALS", "OVER_15_GOALS", "MATCH_RESULT", "DRAW");
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

    @Test
    void capsTheWholeGoalsOverFamilySoOver15CannotFillTheBoardUnderThreeTypeNames() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        // Over 1.5 full match, Over 1.5 HT and Over 1.5 2H are three separate types, so a per-type
        // cap of three still allowed nine slots that all read "Over 1.5 something".
        List<RecommendationSnapshot> day = List.of(
                snapWithMarket(1L, date, 100L, "OVER_15_GOALS", "Over 1.5 Goals", "STRONG", 93.0, 1.22, 1000L),
                snapWithMarket(2L, date, 200L, "OVER_15_GOALS", "Over 1.5 Goals", "STRONG", 92.0, 1.20, 2000L),
                snapWithMarket(3L, date, 300L, "FIRST_HALF_GOALS", "Over 1.5 HT Goals", "STRONG", 91.0, 2.10, 3000L),
                snapWithMarket(4L, date, 400L, "SECOND_HALF_GOALS", "Over 1.5 2H Goals", "STRONG", 90.0, 2.05, 4000L),
                snapWithMarket(5L, date, 500L, "OVER_25_GOALS", "Over 2.5 Goals", "STRONG", 89.0, 1.85, 5000L),
                snapWithMarket(6L, date, 600L, "OVER_GOALS", "Over 2.5 Goals", "STRONG", 88.0, 1.90, 6000L),
                snapWithMarket(7L, date, 700L, "MATCH_RESULT", "Home Win", "STRONG", 74.0, 1.90, 7000L),
                snapWithMarket(8L, date, 800L, "BTTS", "BTTS Yes", "STRONG", 71.0, 1.80, 8000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite.stream()
                .filter(r -> "GOALS_OVER".equals(ElitePicksSelector.marketFamily(r.getType())))
                .count()).isEqualTo(3);
        assertThat(elite).extracting(RecommendationSnapshot::getType)
                .containsExactly("OVER_15_GOALS", "OVER_15_GOALS", "FIRST_HALF_GOALS", "MATCH_RESULT", "BTTS");
    }

    @Test
    void groupsEveryGoalsOverTypeIntoOneFamilyAndLeavesOthersAlone() {
        assertThat(ElitePicksSelector.marketFamily("OVER_15_GOALS")).isEqualTo("GOALS_OVER");
        assertThat(ElitePicksSelector.marketFamily("OVER_25_GOALS")).isEqualTo("GOALS_OVER");
        assertThat(ElitePicksSelector.marketFamily("OVER_GOALS")).isEqualTo("GOALS_OVER");
        assertThat(ElitePicksSelector.marketFamily("FIRST_HALF_GOALS")).isEqualTo("GOALS_OVER");
        assertThat(ElitePicksSelector.marketFamily("SECOND_HALF_GOALS")).isEqualTo("GOALS_OVER");
        // Under goals is the opposite read, not the same cluster.
        assertThat(ElitePicksSelector.marketFamily("UNDER_GOALS")).isEqualTo("UNDER_GOALS");
        assertThat(ElitePicksSelector.marketFamily("BTTS")).isEqualTo("BTTS");
        assertThat(ElitePicksSelector.marketFamily(null)).isEmpty();
    }

    @Test
    void excludesUnpricedPicksBecauseTheyCannotBeJudged() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        List<RecommendationSnapshot> day = List.of(
                unpriced(1L, date, 100L, "FIRST_HALF_GOALS", "Over 1.5 HT Goals", 96.0, 1000L),
                unpriced(2L, date, 200L, "SECOND_HALF_GOALS", "Over 1.5 2H Goals", 95.0, 2000L),
                snapWithMarket(3L, date, 300L, "BTTS", "BTTS Yes", "STRONG", 80.0, 1.80, 3000L)
        );

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(day);

        assertThat(elite).extracting(RecommendationSnapshot::getFixtureId).containsExactly(300L);
        assertThat(elite).allMatch(ElitePicksSelector::hasUsablePrice);
    }

    @Test
    void treatsAnUnbettablePriceAsNoPrice() {
        assertThat(ElitePicksSelector.hasUsablePrice(
                snapWithMarket(1L, LocalDate.of(2026, 8, 15), 100L, "BTTS", "BTTS Yes", "STRONG", 90.0, 1.0, 1000L)))
                .isFalse();
        assertThat(ElitePicksSelector.hasUsablePrice(
                snapWithMarket(2L, LocalDate.of(2026, 8, 15), 200L, "BTTS", "BTTS Yes", "STRONG", 90.0, 1.01, 2000L)))
                .isTrue();
    }

    private static RecommendationSnapshot snap(
            Long id, LocalDate date, Long fixtureId, String type, String confidence,
            double score, double odds, long kickoff) {
        return snapWithMarket(id, date, fixtureId, type, type, confidence, score, odds, kickoff);
    }

    private static RecommendationSnapshot unpriced(
            Long id, LocalDate date, Long fixtureId, String type, String market,
            double score, long kickoff) {
        return RecommendationSnapshot.builder()
                .id(id)
                .snapshotDate(date)
                .fixtureId(fixtureId)
                .type(type)
                .market(market)
                .confidence("STRONG")
                .score(score)
                .odds(null)
                .matchDateUnix(kickoff)
                .outcome(PickOutcome.PENDING)
                .build();
    }

    private static RecommendationSnapshot snapWithMarket(
            Long id, LocalDate date, Long fixtureId, String type, String market, String confidence,
            double score, double odds, long kickoff) {
        return RecommendationSnapshot.builder()
                .id(id)
                .snapshotDate(date)
                .fixtureId(fixtureId)
                .type(type)
                .market(market)
                .confidence(confidence)
                .score(score)
                .odds(odds)
                .matchDateUnix(kickoff)
                .outcome(PickOutcome.PENDING)
                .build();
    }
}
