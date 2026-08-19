package com.jcm.recommendations.soccer.core.recommendation.engine;

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
        assertThat(result.get().getScore()).isGreaterThanOrEqualTo(72.0);
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
    @DisplayName("analyze returns empty when player lists are missing")
    void analyze_withoutPlayers_returnsEmpty() {
        Optional<Recommendation> result = engine.analyze(baseContextBuilder().build());

        assertThat(result).isEmpty();
    }

    private FixtureContext contextWithPlayers(PlayerSeasonStats homePlayer, PlayerSeasonStats awayPlayer) {
        return baseContextBuilder()
                .homePlayers(List.of(homePlayer))
                .awayPlayers(List.of(awayPlayer))
                .build();
    }

    private FixtureContext.FixtureContextBuilder baseContextBuilder() {
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
                        .seasonConcededHome(12)
                        .seasonConcededAway(14)
                        .build())
                .awayTeamStats(TeamSeasonStats.builder()
                        .teamId(2L)
                        .seasonId(100L)
                        .matchesPlayed(10)
                        .seasonConcededHome(16)
                        .seasonConcededAway(18)
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
