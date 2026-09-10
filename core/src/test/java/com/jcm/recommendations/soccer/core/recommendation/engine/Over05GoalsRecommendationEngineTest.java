package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Over05GoalsRecommendationEngineTest {

    private Over05GoalsRecommendationEngine engine;
    private MatchResultRecommendationEngine matchResultEngine;

    @BeforeEach
    void setUp() {
        matchResultEngine = new MatchResultRecommendationEngine();
        engine = new Over05GoalsRecommendationEngine(matchResultEngine);
    }

    @Test
    @DisplayName("getType returns OVER_05_GOALS")
    void getType_returnsOver05Goals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_05_GOALS);
    }

    @Test
    @DisplayName("analyze publishes unpriced Over 0.5 from Match Result tips longer than 6/4")
    void analyze_matchResultLongerThanSixFour_publishesUnpricedOver05() {
        FixtureContext context = dominantHome(1.70);
        Optional<Recommendation> matchResult = matchResultEngine.analyze(context);
        assertThat(matchResult).isPresent();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_05_GOALS);
        assertThat(result.get().getMarket()).isEqualTo("Over 0.5 Goals");
        assertThat(result.get().getOdds()).isNull();
        assertThat(result.get().getScore()).isEqualTo(matchResult.get().getScore());
        assertThat(result.get().getConfidence()).isEqualTo(matchResult.get().getConfidence());
        assertThat(result.get().getFactors().get("derivedFromMatchResult")).isEqualTo(true);
        assertThat(result.get().getFactors().get("matchWinSelection")).isEqualTo("Home Team");
        assertThat(result.get().getFactors().get("matchWinOdds")).isEqualTo(1.70);
    }

    @Test
    @DisplayName("analyze drops Match Result tips at or shorter than 6/4")
    void analyze_matchResultAtOrShorterThanSixFour_isEmpty() {
        assertThat(matchResultEngine.analyze(dominantHome(1.50))).isPresent();
        assertThat(engine.analyze(dominantHome(1.50))).isEmpty();

        assertThat(matchResultEngine.analyze(dominantHome(1.40))).isPresent();
        assertThat(engine.analyze(dominantHome(1.40))).isEmpty();
    }

    @Test
    @DisplayName("analyze is empty when Match Result does not tip")
    void analyze_noMatchResult_isEmpty() {
        assertThat(engine.analyze(dominantAway())).isEmpty();
    }

    private FixtureContext dominantHome(double homeWinOdds) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(9)
                .seasonDrawsHome(6)
                .seasonLossesHome(5)
                .seasonGoalsHome(24)
                .seasonConcededHome(18)
                .seasonGoalDifference(6)
                .ppgHome(1.70)
                .position(6)
                .xgForAvgHome(1.45)
                .xgAgainstAvgHome(1.05)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(4)
                .seasonDrawsAway(6)
                .seasonLossesAway(10)
                .seasonGoalsAway(16)
                .seasonConcededAway(26)
                .seasonGoalDifference(-10)
                .ppgAway(0.90)
                .position(16)
                .xgForAvgAway(0.95)
                .xgAgainstAvgAway(1.50)
                .build();

        return FixtureContext.builder()
                .fixture(Fixture.builder()
                        .id(1000L)
                        .seasonId(1L)
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
                .homeTeamForm(TeamRecentForm.builder()
                        .teamId(1L)
                        .winsHome(3)
                        .drawsHome(1)
                        .lossesHome(1)
                        .build())
                .awayTeamForm(TeamRecentForm.builder()
                        .teamId(2L)
                        .winsAway(1)
                        .drawsAway(1)
                        .lossesAway(3)
                        .build())
                .odds(FixtureOdds.builder()
                        .fixtureId(1000L)
                        .oddsFt1(homeWinOdds)
                        .oddsFtX(3.60)
                        .oddsFt2(5.00)
                        .build())
                .build();
    }

    private FixtureContext dominantAway() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(3)
                .seasonDrawsHome(5)
                .seasonLossesHome(12)
                .seasonGoalsHome(14)
                .seasonConcededHome(30)
                .seasonGoalDifference(-16)
                .ppgHome(0.70)
                .position(18)
                .xgForAvgHome(0.85)
                .xgAgainstAvgHome(1.70)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(10)
                .seasonDrawsAway(5)
                .seasonLossesAway(5)
                .seasonGoalsAway(28)
                .seasonConcededAway(16)
                .seasonGoalDifference(12)
                .ppgAway(1.75)
                .position(4)
                .xgForAvgAway(1.55)
                .xgAgainstAvgAway(0.95)
                .build();

        return FixtureContext.builder()
                .fixture(Fixture.builder()
                        .id(2000L)
                        .seasonId(1L)
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
                .odds(FixtureOdds.builder()
                        .fixtureId(2000L)
                        .oddsFt1(6.00)
                        .oddsFtX(4.00)
                        .oddsFt2(1.55)
                        .build())
                .build();
    }
}
