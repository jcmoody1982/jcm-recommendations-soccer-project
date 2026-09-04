package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Over05GoalsRecommendationEngineTest {

    private Over05GoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new Over05GoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns OVER_05_GOALS")
    void getType_returnsOver05Goals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_05_GOALS);
    }

    @Test
    @DisplayName("analyze publishes Over 0.5 when the price is above 1.20")
    void analyze_withBackablePrice_returnsOver05() {
        Optional<Recommendation> result = engine.analyze(context(1.45));

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Over 0.5 Goals");
        assertThat(result.get().getOdds()).isEqualTo(1.45);
        assertThat(result.get().getScore()).isGreaterThan(70.0).isLessThan(97.0);
    }

    @Test
    @DisplayName("analyze drops an unpriced or 1.20-or-shorter Over 0.5")
    void analyze_requiresPriceAbove120() {
        assertThat(engine.analyze(context(null))).isEmpty();
        assertThat(engine.analyze(context(1.20))).isEmpty();
        assertThat(engine.analyze(context(1.01))).isEmpty();
    }

    private FixtureContext context(Double over05Odds) {
        int matches = 10;
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(14)
                .seasonGoalsAway(10)
                .seasonConcededHome(10)
                .seasonConcededAway(12)
                .seasonFailedToScoreOverall(2)
                .seasonCleanSheetsOverall(3)
                .build();
        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(12)
                .seasonGoalsAway(11)
                .seasonConcededHome(11)
                .seasonConcededAway(10)
                .seasonFailedToScoreOverall(2)
                .seasonCleanSheetsOverall(2)
                .build();

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
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(FixtureOdds.builder().fixtureId(1000L).oddsFtOver05(over05Odds).build())
                .build();
    }
}
