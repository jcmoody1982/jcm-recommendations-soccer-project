package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Shared player-prop maths, pinned independently of the scorer/assist specs. */
class PlayerPropRecommendationEngineTest {

    @Test
    @DisplayName("probabilityOfAtLeastOne is a Poisson tail and zero for non-positive lambda")
    void poissonTail() {
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(0)).isZero();
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(-1)).isZero();
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(1.0))
                .isCloseTo(100.0 * (1 - Math.exp(-1)), within(1e-9));
    }

    @Test
    @DisplayName("shrinkPer90 adds ten matches of prior evidence to the season count")
    void shrinkage() {
        assertThat(PlayerPropRecommendationEngine.shrinkPer90(0, 0, 0.15)).isCloseTo(0.15, within(1e-9));
        // 9 goals in 1800 minutes is 0.45 raw; plus 1.5 prior goals over 900 pseudo-minutes.
        assertThat(PlayerPropRecommendationEngine.shrinkPer90(9, 1800, 0.15)).isCloseTo(0.35, within(1e-9));
        double hotStart = PlayerPropRecommendationEngine.shrinkPer90(6, 600, 0.15);
        double fullSeason = PlayerPropRecommendationEngine.shrinkPer90(30, 3000, 0.15);
        assertThat(hotStart).isLessThan(fullSeason);
    }

    @Test
    @DisplayName("fixtureFactor scales by this fixture's expected team goals and is clamped")
    void fixtureFactor() {
        assertThat(PlayerPropRecommendationEngine.fixtureFactor(1.5, null)).isEqualTo(1.0);
        assertThat(PlayerPropRecommendationEngine.fixtureFactor(1.5, 1.2)).isCloseTo(1.25, within(1e-9));
        assertThat(PlayerPropRecommendationEngine.fixtureFactor(3.0, 1.0)).isEqualTo(1.60);
        assertThat(PlayerPropRecommendationEngine.fixtureFactor(0.3, 1.0)).isEqualTo(0.60);
    }

    @Test
    @DisplayName("availability is the share of team matches played, capped at 1 and neutral when unknown")
    void availability() {
        assertThat(PlayerPropRecommendationEngine.availability(apps(9), 10)).isCloseTo(0.9, within(1e-9));
        assertThat(PlayerPropRecommendationEngine.availability(apps(12), 10)).isEqualTo(1.0);
        assertThat(PlayerPropRecommendationEngine.availability(apps(9), 0)).isEqualTo(1.0);
        assertThat(PlayerPropRecommendationEngine.availability(PlayerSeasonStats.builder().build(), 10)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("expectedMinutes is the per-match average capped at 90")
    void expectedMinutes() {
        assertThat(PlayerPropRecommendationEngine.expectedMinutes(PlayerSeasonStats.builder().minPerMatch(120).build()))
                .isEqualTo(90.0);
        assertThat(PlayerPropRecommendationEngine.expectedMinutes(PlayerSeasonStats.builder().minPerMatch(78).build()))
                .isEqualTo(78.0);
    }

    @Test
    @DisplayName("seasonCount prefers the raw count and falls back to per-90 x minutes")
    void seasonCount() {
        assertThat(PlayerPropRecommendationEngine.seasonCount(7, 0.9, 900)).isEqualTo(7);
        assertThat(PlayerPropRecommendationEngine.seasonCount(null, 0.5, 1800)).isEqualTo(10);
        assertThat(PlayerPropRecommendationEngine.seasonCount(null, null, 1800)).isZero();
    }

    @Test
    @DisplayName("seasonGoalsPerMatch prefers the feed average, then goals over matches")
    void seasonGoalsPerMatch() {
        assertThat(TeamGoalsModel.seasonGoalsPerMatch(TeamSeasonStats.builder().scoredAvgOverall(1.7).build()))
                .isEqualTo(1.7);
        assertThat(TeamGoalsModel.seasonGoalsPerMatch(
                TeamSeasonStats.builder().seasonGoalsOverall(18).matchesPlayed(12).build())).isEqualTo(1.5);
        assertThat(TeamGoalsModel.seasonGoalsPerMatch(TeamSeasonStats.builder().build())).isNull();
    }

    @Test
    @DisplayName("displayName prefers knownAs, then fullName, then joined name parts")
    void displayNameFallbacks() {
        assertThat(PlayerPropRecommendationEngine.displayName(null)).isNull();
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().knownAs(" Bruno ").fullName("Bruno Fernandes").build())).isEqualTo("Bruno");
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().knownAs(" ").fullName("Bruno Fernandes").build())).isEqualTo("Bruno Fernandes");
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().firstName("Bruno").lastName(" ").build())).isEqualTo("Bruno");
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().lastName("Fernandes").build())).isEqualTo("Fernandes");
        assertThat(PlayerPropRecommendationEngine.displayName(PlayerSeasonStats.builder().build())).isNull();
    }

    @Test
    @DisplayName("isGoalkeeper matches any position containing 'goal'")
    void goalkeeperDetection() {
        assertThat(PlayerPropRecommendationEngine.isGoalkeeper("Goalkeeper")).isTrue();
        assertThat(PlayerPropRecommendationEngine.isGoalkeeper("Midfielder")).isFalse();
        assertThat(PlayerPropRecommendationEngine.isGoalkeeper(null)).isFalse();
    }

    private static PlayerSeasonStats apps(int appearances) {
        return PlayerSeasonStats.builder().appearancesOverall(appearances).build();
    }
}
