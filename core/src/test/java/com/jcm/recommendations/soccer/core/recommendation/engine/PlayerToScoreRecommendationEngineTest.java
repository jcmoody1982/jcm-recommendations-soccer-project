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
                scorer(10L, "Mohamed Salah", 0.72, 18, 1500, 1),
                scorer(11L, "Squad Forward", 0.28, 10, 700, 3)));

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.PLAYER_TO_SCORE);
        assertThat(result.get().getMarket()).isEqualTo("Mohamed Salah to score");
        assertThat(result.get().getOdds()).isNull();
        assertThat(result.get().getFactors()).containsEntry("playerId", 10L);
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze publishes a probability the market can actually produce")
    void analyze_eliteScorer_staysWithinRealisticRange() {
        Optional<Recommendation> result = engine.analyze(contextWithOpponentConceded(
                scorer(10L, "Elite Striker", 0.55, 20, 1600, null),
                NEUTRAL_DEFENCE_CONCEDED));

        assertThat(result).isPresent();
        // A 0.55-per-90 striker over a full match is a ~42% chance to score. The old weighted
        // index published 58 as its *floor*, so guard the whole band, not just the 100 clamp.
        assertThat(result.get().getScore()).isLessThan(50.0);
        assertThat(result.get().getScore()).isGreaterThan(25.0);
    }

    @Test
    @DisplayName("analyze prefers the proven scorer over an equal-rate small sample")
    void analyze_thinSample_isShrunkTowardPrior() {
        Optional<Recommendation> result = engine.analyze(contextWithPlayers(
                scorer(10L, "Proven Starter", 0.60, 20, 1600, null),
                scorer(11L, "Hot Streak Sub", 0.60, 6, 400, null)));

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Proven Starter to score");
    }

    @Test
    @DisplayName("analyze scores a part-time player below a full-match player on the same rate")
    void analyze_expectedMinutes_lowerTheScore() {
        PlayerSeasonStats fullMatch = scorer(10L, "Ninety Minute Man", 0.70, 20, 1600, null);
        fullMatch.setMinPerMatch(90);
        PlayerSeasonStats partial = scorer(10L, "Hour Player", 0.70, 20, 1600, null);
        partial.setMinPerMatch(55);

        double fullScore = scoreFor(fullMatch);
        double partialScore = scoreFor(partial);

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

    private double scoreFor(PlayerSeasonStats player) {
        Optional<Recommendation> result = engine.analyze(
                contextWithOpponentConceded(player, NEUTRAL_DEFENCE_CONCEDED));
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
