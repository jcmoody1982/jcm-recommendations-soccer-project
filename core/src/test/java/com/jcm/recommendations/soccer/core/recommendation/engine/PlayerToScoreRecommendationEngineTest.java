package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerToScoreRecommendationEngineTest {

    /** Goals conceded over 10 matches that lands the opponent on the league-average rate. */
    private static final int NEUTRAL_DEFENCE_CONCEDED = 14;

    /** Soft defence so calibrated lambda clears the raised moderate floor in fixture tests. */
    private static final int LEAKY_DEFENCE_CONCEDED = 25;

    private PlayerToScoreRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PlayerToScoreRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns PLAYER_TO_SCORE")
    void getType_returnsPlayerToScore() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.PLAYER_TO_SCORE);
    }

    @Test
    @DisplayName("analyze picks the highest per-90 regular across both squads")
    void analyze_picksBestScorer() {
        Optional<Recommendation> result = engine.analyze(contextWithPlayers(
                scorer(10L, "Mohamed Salah", 0.85, 18, 1500, 1),
                scorer(11L, "Squad Forward", 0.28, 10, 700, 3)));

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.PLAYER_TO_SCORE);
        assertThat(result.get().getMarket()).isEqualTo("Mohamed Salah to score");
        assertThat(result.get().getOdds()).isNull();
        assertThat(result.get().getFactors()).containsEntry("playerId", 10L);
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
        assertThat(result.get().getScore()).isLessThan(55.0);
    }

    @Test
    @DisplayName("analyze publishes a calibrated probability below the raw Poisson ceiling")
    void analyze_eliteScorer_staysWithinRealisticRange() {
        Optional<Recommendation> result = engine.analyze(contextWithOpponentConceded(
                scorer(10L, "Elite Striker", 0.85, 20, 1600, null),
                LEAKY_DEFENCE_CONCEDED));

        assertThat(result).isPresent();
        // Raw Poisson on 0.85 per-90 over 90 minutes is ~57%; calibration + floors keep the
        // published score in the mid band the board can defend.
        assertThat(result.get().getScore()).isLessThan(50.0);
        assertThat(result.get().getScore()).isGreaterThan(35.0);
    }

    @Test
    @DisplayName("analyze prefers the proven scorer over an equal-rate small sample")
    void analyze_thinSample_isShrunkTowardPrior() {
        Optional<Recommendation> result = engine.analyze(contextWithPlayers(
                scorer(10L, "Proven Starter", 0.80, 20, 1600, null),
                scorer(11L, "Hot Streak Sub", 0.80, 6, 400, null)));

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Proven Starter to score");
    }

    @Test
    @DisplayName("analyze scores a part-time player below a full-match player on the same rate")
    void analyze_expectedMinutes_lowerTheScore() {
        PlayerSeasonStats fullMatch = scorer(10L, "Ninety Minute Man", 0.85, 20, 1600, null);
        fullMatch.setMinPerMatch(90);
        PlayerSeasonStats partial = scorer(10L, "Hour Player", 0.85, 20, 1600, null);
        partial.setMinPerMatch(70);

        double fullScore = scoreFor(fullMatch, LEAKY_DEFENCE_CONCEDED);
        double partialScore = scoreFor(partial, LEAKY_DEFENCE_CONCEDED);

        assertThat(partialScore).isLessThan(fullScore);
    }

    @Test
    @DisplayName("analyze skips keepers and low-minute players")
    void analyze_skipsKeepersAndBenchPlayers() {
        PlayerSeasonStats keeper = scorer(20L, "Alisson", 0.05, 20, 1800, null);
        keeper.setPosition("Goalkeeper");
        PlayerSeasonStats sub = scorer(21L, "Impact Sub", 0.90, 2, 80, 1);
        sub.setMinPerMatch(20);

        Optional<Recommendation> result = engine.analyze(contextWithPlayers(keeper, sub));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze drops a marginal scorer below the moderate threshold")
    void analyze_marginalScorer_isNotPublished() {
        Optional<Recommendation> result = engine.analyze(contextWithOpponentConceded(
                scorer(10L, "Occasional Scorer", 0.26, 8, 500, null),
                NEUTRAL_DEFENCE_CONCEDED));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze builds the market from first and last name when known-as is missing")
    void analyze_usesFirstAndLastNameWhenKnownAsMissing() {
        PlayerSeasonStats namedByParts = scorer(10L, null, 0.85, 18, 1500, 1);
        namedByParts.setFirstName("Mohamed");
        namedByParts.setLastName("Salah");

        Optional<Recommendation> result = engine.analyze(contextWithPlayers(
                namedByParts,
                scorer(11L, "Squad Forward", 0.28, 10, 700, 3)));

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Mohamed Salah to score");
        assertThat(result.get().getFactors()).containsEntry("playerName", "Mohamed Salah");
    }

    @Test
    @DisplayName("analyze skips a tip when no candidate has a usable name")
    void analyze_namelessCandidates_areSkipped() {
        PlayerSeasonStats nameless = scorer(10L, null, 0.85, 18, 1500, 1);

        Optional<Recommendation> result = engine.analyze(
                contextWithOpponentConceded(nameless, LEAKY_DEFENCE_CONCEDED));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze prefers the best named scorer over a nameless higher-rated candidate")
    void analyze_prefersNamedCandidateOverNamelessLeader() {
        PlayerSeasonStats namelessLeader = scorer(10L, null, 0.90, 20, 1600, 1);
        PlayerSeasonStats namedRunnerUp = scorer(11L, "Named Forward", 0.80, 18, 1500, 2);
        namedRunnerUp.setClubTeamId(1L);

        Optional<Recommendation> result = engine.analyze(baseContextBuilder(LEAKY_DEFENCE_CONCEDED)
                .homePlayers(List.of(namelessLeader, namedRunnerUp))
                .awayPlayers(List.of())
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Named Forward to score");
    }

    @Test
    @DisplayName("displayName prefers known-as, then full name, then first/last parts")
    void displayName_fallsBackThroughNameFields() {
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().knownAs("Salah").fullName("Mohamed Salah").build()))
                .isEqualTo("Salah");
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().fullName("Mohamed Salah").firstName("Mo").build()))
                .isEqualTo("Mohamed Salah");
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().firstName("Mohamed").lastName("Salah").build()))
                .isEqualTo("Mohamed Salah");
        assertThat(PlayerPropRecommendationEngine.displayName(
                PlayerSeasonStats.builder().playerId(1L).build()))
                .isNull();
    }

    @Test
    @DisplayName("analyze returns empty when player lists are missing")
    void analyze_withoutPlayers_returnsEmpty() {
        Optional<Recommendation> result = engine.analyze(baseContextBuilder(NEUTRAL_DEFENCE_CONCEDED).build());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("probabilityOfAtLeastOne follows the Poisson complement")
    void probabilityOfAtLeastOne_matchesPoisson() {
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(0.0)).isZero();
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(0.55))
                .isCloseTo(42.3, org.assertj.core.data.Offset.offset(0.1));
        assertThat(PlayerPropRecommendationEngine.probabilityOfAtLeastOne(10.0)).isLessThan(100.0);
    }

    @Test
    @DisplayName("opponentFactor stays within the tight 0.90–1.10 band")
    void opponentFactor_isClampedTightly() {
        assertThat(PlayerPropRecommendationEngine.opponentFactor(0.5)).isEqualTo(0.90);
        assertThat(PlayerPropRecommendationEngine.opponentFactor(1.35)).isEqualTo(1.0);
        assertThat(PlayerPropRecommendationEngine.opponentFactor(3.0)).isEqualTo(1.10);
    }

    private double scoreFor(PlayerSeasonStats player, int concededAway) {
        Optional<Recommendation> result = engine.analyze(
                contextWithOpponentConceded(player, concededAway));
        assertThat(result).isPresent();
        return result.get().getScore();
    }

    private FixtureContext contextWithPlayers(PlayerSeasonStats homePlayer, PlayerSeasonStats awayPlayer) {
        return baseContextBuilder(18)
                .homePlayers(List.of(homePlayer))
                .awayPlayers(List.of(awayPlayer))
                .build();
    }

    private FixtureContext contextWithOpponentConceded(PlayerSeasonStats homePlayer, int concededAway) {
        return baseContextBuilder(concededAway)
                .homePlayers(List.of(homePlayer))
                .awayPlayers(List.of())
                .build();
    }

    private FixtureContext.FixtureContextBuilder baseContextBuilder(int awayConcededAway) {
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
                .homeTeamStats(TeamSeasonStats.builder()
                        .teamId(1L)
                        .seasonId(100L)
                        .matchesPlayed(10)
                        .matchesPlayedHome(10)
                        .matchesPlayedAway(10)
                        .seasonConcededHome(12)
                        .seasonConcededAway(14)
                        .build())
                .awayTeamStats(TeamSeasonStats.builder()
                        .teamId(2L)
                        .seasonId(100L)
                        .matchesPlayed(10)
                        .matchesPlayedHome(10)
                        .matchesPlayedAway(10)
                        .seasonConcededHome(16)
                        .seasonConcededAway(awayConcededAway)
                        .build());
    }

    private static PlayerSeasonStats scorer(
            Long playerId, String knownAs, double per90, int appearances, int minutes, Integer rank) {
        return PlayerSeasonStats.builder()
                .playerId(playerId)
                .seasonId(100L)
                .clubTeamId(playerId == 10L ? 1L : 2L)
                .knownAs(knownAs)
                .position("Forward")
                .appearancesOverall(appearances)
                .minutesPlayedOverall(minutes)
                .minPerMatch(80)
                .goalsPer90Overall(per90)
                .goalsPer90Home(per90)
                .goalsPer90Away(per90)
                .rankInClubTopScorer(rank)
                .build();
    }
}
