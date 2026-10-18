package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.InformationalMarkets;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
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

class DrawWatchRecommendationEngineTest {

    private DrawRecommendationEngine drawEngine;
    private DrawWatchRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        drawEngine = new DrawRecommendationEngine();
        engine = new DrawWatchRecommendationEngine(drawEngine);
    }

    @Test
    @DisplayName("getType returns DRAW_WATCH, an information-only market")
    void getType_isInformational() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.DRAW_WATCH);
        assertThat(InformationalMarkets.isInformational(engine.getType())).isTrue();
    }

    @Test
    @DisplayName("a level match whose draw price is too short is watched, not tipped")
    void levelMatchAtShortPrice_isWatched() {
        FixtureContext context = levelContext(odds(2.45, 3.10, 2.80));

        assertThat(drawEngine.analyze(context)).isEmpty();
        Optional<Recommendation> watch = engine.analyze(context);

        assertThat(watch).isPresent();
        assertThat(watch.get().getType()).isEqualTo(RecommendationType.DRAW_WATCH);
        assertThat(watch.get().getMarket()).isEqualTo("Draw");
        assertThat(watch.get().getOdds()).isEqualTo(3.10);
        assertThat(watch.get().getConfidence()).isEqualTo(ConfidenceLevel.WEAK);
        assertThat(watch.get().getScore()).isBetween(20.0, 40.0);
        assertThat((String) watch.get().getFactors().get("watchReason")).contains("edge");
        assertThat(watch.get().getDescription()).contains("not a tip");
    }

    @Test
    @DisplayName("a fixture that clears the price goes to the Draw board, never both")
    void publishedDraw_isNotAlsoWatched() {
        FixtureContext context = levelContext(odds(2.45, 3.60, 2.80));

        assertThat(drawEngine.analyze(context)).isPresent();
        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("a high-scoring fixture is not watched however the price looks")
    void highScoringFixture_isNotWatched() {
        FixtureContext context = FixtureContext.builder()
                .fixture(fixture())
                .homeTeam(team(1L, "Home Team"))
                .awayTeam(team(2L, "Away Team"))
                .homeTeamStats(homeStats().seasonGoalsHome(40).seasonConcededHome(36)
                        .xgForAvgHome(2.0).xgAgainstAvgHome(1.8).build())
                .awayTeamStats(awayStats().seasonGoalsAway(36).seasonConcededAway(38)
                        .xgForAvgAway(1.8).xgAgainstAvgAway(2.0).build())
                .odds(odds(2.45, 3.10, 2.80))
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("no complete price means nothing to watch")
    void withoutCompletePrice_isNotWatched() {
        FixtureContext context = levelContext(FixtureOdds.builder().fixtureId(101L).oddsFtX(3.10).build());

        assertThat(engine.analyze(context)).isEmpty();
    }

    private FixtureContext levelContext(FixtureOdds odds) {
        return FixtureContext.builder()
                .fixture(fixture())
                .homeTeam(team(1L, "Home Team"))
                .awayTeam(team(2L, "Away Team"))
                .homeTeamStats(homeStats().build())
                .awayTeamStats(awayStats().build())
                .odds(odds)
                .build();
    }

    private TeamSeasonStats.TeamSeasonStatsBuilder homeStats() {
        return TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.5)
                .position(8)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.15)
                .xgForAvgHome(1.15)
                .xgAgainstAvgHome(1.20);
    }

    private TeamSeasonStats.TeamSeasonStatsBuilder awayStats() {
        return TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.10)
                .xgForAvgAway(0.95)
                .xgAgainstAvgAway(1.10);
    }

    private FixtureOdds odds(double home, double draw, double away) {
        return FixtureOdds.builder()
                .fixtureId(101L)
                .oddsFt1(home)
                .oddsFtX(draw)
                .oddsFt2(away)
                .build();
    }

    private Fixture fixture() {
        return Fixture.builder()
                .id(101L)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .seasonId(1L)
                .status("upcoming")
                .build();
    }

    private Team team(Long id, String name) {
        return Team.builder().id(id).name(name).build();
    }
}
