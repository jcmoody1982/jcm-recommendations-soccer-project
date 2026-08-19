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

class PlayerToAssistRecommendationEngineTest {

    private PlayerToAssistRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PlayerToAssistRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns PLAYER_TO_ASSIST")
    void getType_returnsPlayerToAssist() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.PLAYER_TO_ASSIST);
    }

    @Test
    @DisplayName("analyze picks the creator with the strongest assist per-90")
    void analyze_picksBestCreator() {
        Optional<Recommendation> result = engine.analyze(FixtureContext.builder()
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
                        .teamId(1L).seasonId(100L).matchesPlayed(10)
                        .seasonConcededHome(12).seasonConcededAway(14).build())
                .awayTeamStats(TeamSeasonStats.builder()
                        .teamId(2L).seasonId(100L).matchesPlayed(10)
                        .seasonConcededHome(16).seasonConcededAway(18).build())
                .homePlayers(List.of(creator(30L, "Bruno Fernandes", 0.48, 16, 1400)))
                .awayPlayers(List.of(creator(31L, "Wide Midfielder", 0.22, 12, 900)))
                .build());

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.PLAYER_TO_ASSIST);
        assertThat(result.get().getMarket()).isEqualTo("Bruno Fernandes to assist");
        assertThat(result.get().getOdds()).isNull();
        assertThat(result.get().getScore()).isGreaterThanOrEqualTo(58.0);
    }

    private static PlayerSeasonStats creator(Long playerId, String knownAs, double per90, int appearances, int minutes) {
        return PlayerSeasonStats.builder()
                .playerId(playerId)
                .seasonId(100L)
                .clubTeamId(playerId == 30L ? 1L : 2L)
                .knownAs(knownAs)
                .position("Midfielder")
                .appearancesOverall(appearances)
                .minutesPlayedOverall(minutes)
                .minPerMatch(80)
                .assistsPer90Overall(per90)
                .assistsHome((int) Math.round(per90 * minutes / 90.0))
                .minutesPlayedHome(minutes)
                .build();
    }
}
