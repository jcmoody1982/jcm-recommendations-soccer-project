package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Shared player-prop pipeline (filters, shrinkage, minutes, opponent factor, naming, confidence)
 * exercised through a minimal spec so it is pinned independently of the scorer/assist tuning.
 */
class PlayerPropRecommendationEngineTest {

    private static final class StubPropEngine extends PlayerPropRecommendationEngine {
        private final PropSpec spec;

        StubPropEngine(double strong, double moderate) {
            this.spec = new PropSpec(RecommendationType.PLAYER_TO_ASSIST, "to assist", 0.20, 0.80, 0.15, strong, moderate);
        }

        @Override
        protected PropSpec spec() {
            return spec;
        }

        @Override
        protected double per90(PlayerSeasonStats player, boolean isHome) {
            Double rate = player.getAssistsPer90Overall();
            return rate == null ? 0.0 : rate;
        }
    }

    private static final StubPropEngine LENIENT = new StubPropEngine(40.0, 10.0);

    @Test
    @DisplayName("probabilityOfAtLeastOne is a Poisson tail and zero for non-positive lambda")
    void poissonTail() {
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(0)).isZero();
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(-1)).isZero();
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(1.0))
                .isCloseTo(100.0 * (1 - Math.exp(-1)), within(1e-9));
    }

    @Test
    @DisplayName("shrinkPer90 pulls thin samples toward the prior and trusts a full season")
    void shrinkage() {
        assertThat(PlayerPropRecommendationEngine.shrinkPer90(0.9, 0, 0.2)).isEqualTo(0.2);
        double thin = PlayerPropRecommendationEngine.shrinkPer90(0.9, 300, 0.2);
        double regular = PlayerPropRecommendationEngine.shrinkPer90(0.9, 2700, 0.2);
        assertThat(thin).isCloseTo(0.2 + 0.7 * 300 / 750.0, within(1e-9));
        assertThat(regular).isGreaterThan(thin).isLessThan(0.9);
    }

    @Test
    @DisplayName("expectedMinutes defaults to 70 and is capped at 90")
    void expectedMinutes() {
        assertThat(PlayerPropRecommendationEngine.expectedMinutes(PlayerSeasonStats.builder().build())).isEqualTo(70.0);
        assertThat(PlayerPropRecommendationEngine.expectedMinutes(PlayerSeasonStats.builder().minPerMatch(120).build()))
                .isEqualTo(90.0);
        assertThat(PlayerPropRecommendationEngine.expectedMinutes(PlayerSeasonStats.builder().minPerMatch(63).build()))
                .isEqualTo(63.0);
    }

    @Test
    @DisplayName("opponentFactor is clamped to 0.90–1.10 and neutral without data")
    void opponentFactorClamp() {
        assertThat(PlayerPropRecommendationEngine.opponentFactor(0)).isEqualTo(1.0);
        assertThat(PlayerPropRecommendationEngine.opponentFactor(0.2)).isEqualTo(0.90);
        assertThat(PlayerPropRecommendationEngine.opponentFactor(3.0)).isEqualTo(1.10);
        assertThat(PlayerPropRecommendationEngine.opponentFactor(1.35)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("venuePer90 falls back below one full match of venue minutes")
    void venuePer90() {
        assertThat(PlayerPropRecommendationEngine.venuePer90(2, 89, 0.33)).isEqualTo(0.33);
        assertThat(PlayerPropRecommendationEngine.venuePer90(3, 270, 0.33)).isCloseTo(1.0, within(1e-9));
        assertThat(PlayerPropRecommendationEngine.venuePer90(null, null, 0.4)).isEqualTo(0.4);
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
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().firstName("Bruno").lastName("Fernandes").build())).isEqualTo("Bruno Fernandes");
        assertThat(PlayerPropRecommendationEngine.displayName(PlayerSeasonStats.builder().build())).isNull();
    }

    @Test
    @DisplayName("isGoalkeeper matches any position containing 'goal'")
    void goalkeeperDetection() {
        assertThat(PlayerPropRecommendationEngine.isGoalkeeper("Goalkeeper")).isTrue();
        assertThat(PlayerPropRecommendationEngine.isGoalkeeper("Midfielder")).isFalse();
        assertThat(PlayerPropRecommendationEngine.isGoalkeeper(null)).isFalse();
    }

    @Test
    @DisplayName("not applicable without player stats")
    void requiresPlayers() {
        assertThat(LENIENT.isApplicable(context(null, null))).isFalse();
        assertThat(LENIENT.analyze(context(List.of(), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("filters drop goalkeepers, thin samples, bit-part players and low rates")
    void filters() {
        PlayerSeasonStats keeper = player(1L, "Keeper", 0.9, 20, 1800, 90);
        keeper.setPosition("Goalkeeper");
        PlayerSeasonStats thin = player(2L, "Thin", 0.9, 4, 260, 65);
        PlayerSeasonStats bitPart = player(3L, "Bit Part", 0.9, 20, 600, 30);
        PlayerSeasonStats lowRate = player(4L, "Low Rate", 0.10, 20, 1800, 90);
        PlayerSeasonStats noId = player(null, "No Id", 0.9, 20, 1800, 90);

        assertThat(LENIENT.analyze(context(Arrays.asList(keeper, thin, bitPart, null), List.of(lowRate, noId)))).isEmpty();
    }

    @Test
    @DisplayName("appearances OR minutes is enough to pass the sample filter")
    void sampleFilterIsEitherOr() {
        PlayerSeasonStats fewAppsManyMinutes = player(5L, "Long Minutes", 0.6, 4, 400, 90);

        assertThat(LENIENT.analyze(context(List.of(fewAppsManyMinutes), null))).isPresent();
    }

    @Test
    @DisplayName("picks the strongest named candidate across both teams and records factors")
    void picksBestAcrossTeams() {
        PlayerSeasonStats homeCreator = player(10L, "Home Creator", 0.40, 20, 1800, 85);
        PlayerSeasonStats awayStar = player(20L, "Away Star", 0.70, 20, 1800, 85);
        PlayerSeasonStats namelessBest = player(30L, null, 0.95, 20, 1800, 90);

        Recommendation rec = LENIENT.analyze(context(List.of(homeCreator), new ArrayList<>(List.of(awayStar, namelessBest))))
                .orElseThrow();

        assertThat(rec.getMarket()).isEqualTo("Away Star to assist");
        assertThat(rec.getOdds()).isNull();
        assertThat(rec.getFactors())
                .containsEntry("playerId", 20L)
                .containsEntry("isHome", false)
                .containsEntry("teamName", "Away Team")
                .containsEntry("marketVerb", "to assist");
        double lambda = (double) rec.getFactors().get("expectedEvents");
        assertThat(rec.getScore()).isCloseTo(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(lambda), within(1e-9));
    }

    @Test
    @DisplayName("only nameless candidates means no tip")
    void namelessOnly() {
        assertThat(LENIENT.analyze(context(List.of(player(30L, null, 0.95, 20, 1800, 90)), null))).isEmpty();
    }

    @Test
    @DisplayName("confidence bands follow spec thresholds and WEAK is withheld")
    void confidenceBands() {
        FixtureContext ctx = context(List.of(player(10L, "Creator", 0.6, 20, 1800, 85)), null);
        double score = LENIENT.analyze(ctx).orElseThrow().getScore();

        assertThat(new StubPropEngine(score - 1, score - 5).analyze(ctx))
                .get().extracting(Recommendation::getConfidence).isEqualTo(ConfidenceLevel.STRONG);
        assertThat(new StubPropEngine(score + 1, score - 1).analyze(ctx))
                .get().extracting(Recommendation::getConfidence).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(new StubPropEngine(score + 5, score + 1).analyze(ctx)).isEmpty();
    }

    private static PlayerSeasonStats player(Long id, String knownAs, double per90, int apps, int minutes, int minPerMatch) {
        return PlayerSeasonStats.builder()
                .playerId(id)
                .seasonId(100L)
                .knownAs(knownAs)
                .position("Midfielder")
                .appearancesOverall(apps)
                .minutesPlayedOverall(minutes)
                .minPerMatch(minPerMatch)
                .assistsPer90Overall(per90)
                .build();
    }

    private static FixtureContext context(List<PlayerSeasonStats> home, List<PlayerSeasonStats> away) {
        return FixtureContext.builder()
                .fixture(Fixture.builder()
                        .id(1000L)
                        .seasonId(100L)
                        .homeTeamId(1L)
                        .awayTeamId(2L)
                        .homeTeamName("Home Team")
                        .awayTeamName("Away Team")
                        .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                        .status("incomplete")
                        .build())
                .homeTeam(Team.builder().id(1L).name("Home Team").build())
                .awayTeam(Team.builder().id(2L).name("Away Team").build())
                .homeTeamStats(TeamSeasonStats.builder().teamId(1L).seasonId(100L).matchesPlayed(10)
                        .matchesPlayedHome(10).matchesPlayedAway(10)
                        .seasonConcededHome(12).seasonConcededAway(14).build())
                .awayTeamStats(TeamSeasonStats.builder().teamId(2L).seasonId(100L).matchesPlayed(10)
                        .matchesPlayedHome(10).matchesPlayedAway(10)
                        .seasonConcededHome(16).seasonConcededAway(25).build())
                .homePlayers(home)
                .awayPlayers(away)
                .build();
    }
}
