package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("a strong home attack against a leaky away defence publishes the home side, unpriced and Moderate")
    void analyze_strongHomeAttack_publishesHomeTeam() {
        FixtureContext context = context(
                homeSide(10, 25, 8, 1, 5, 2.2, 0.9),
                awaySide(10, 8, 20, 5, 1, 0.9, 1.9));

        Recommendation rec = engine.analyze(context).orElseThrow();

        assertThat(rec.getType()).isEqualTo(RecommendationType.OVER_05_GOALS);
        assertThat(rec.getMarket()).isEqualTo("Home Team Over 0.5 Goals");
        assertThat(rec.getOdds()).isNull();
        assertThat(rec.getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(rec.getScore())
                .isGreaterThanOrEqualTo(Over05GoalsRecommendationEngine.THRESHOLD_MODERATE)
                .isLessThan(Over05GoalsRecommendationEngine.MAX_REALISTIC_PROBABILITY);
        assertThat(rec.getFactors()).containsEntry("venue", "Home").containsEntry("team", "Home Team");
        assertThat(rec.getFactors()).containsKeys("teamExpectedGoals", "poissonProbability",
                "teamScoresPct", "opponentConcedesPct");
    }

    @Test
    @DisplayName("away sides are considered too, not only home teams")
    void analyze_strongAwayAttack_publishesAwayTeam() {
        FixtureContext context = context(
                homeSide(10, 8, 22, 5, 1, 0.9, 2.0),
                awaySide(10, 24, 8, 1, 5, 2.1, 0.9));

        Recommendation rec = engine.analyze(context).orElseThrow();

        assertThat(rec.getMarket()).isEqualTo("Away Team Over 0.5 Goals");
        assertThat(rec.getFactors()).containsEntry("venue", "Away");
    }

    @Test
    @DisplayName("an average fixture sits below the bar for both sides")
    void analyze_averageFixture_isEmpty() {
        FixtureContext context = context(
                homeSide(10, 13, 13, 3, 3, 1.3, 1.3),
                awaySide(10, 13, 13, 3, 3, 1.3, 1.3));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("fewer than three matches at the venue is too little evidence to publish")
    void analyze_shortVenueSample_isEmpty() {
        FixtureContext context = context(
                homeSide(2, 6, 1, 0, 1, 2.5, 0.8),
                awaySide(10, 8, 20, 5, 1, 0.9, 1.9));

        assertThat(engine.isApplicable(context)).isFalse();
        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("probability rises with the team's attack and stays under the realistic ceiling")
    void estimate_monotonicAndCapped() {
        FixtureContext modest = context(homeSide(10, 18, 10, 2, 3, 1.7, 1.1), awaySide(10, 10, 18, 4, 2, 1.0, 1.7));
        FixtureContext extreme = context(homeSide(20, 80, 5, 0, 15, 4.0, 0.3), awaySide(20, 5, 70, 15, 0, 0.3, 3.5));

        double modestScore = engine.estimate(modest, true).score();
        double extremeScore = engine.estimate(extreme, true).score();

        assertThat(extremeScore).isGreaterThan(modestScore);
        assertThat(extremeScore).isLessThan(Over05GoalsRecommendationEngine.MAX_REALISTIC_PROBABILITY);
    }

    @Test
    @DisplayName("recent form feeds into the team's expected goals")
    void estimate_usesRecentFormWhenPresent() {
        FixtureContext base = context(homeSide(10, 18, 10, 2, 3, 1.7, 1.1), awaySide(10, 10, 18, 4, 2, 1.0, 1.7));
        FixtureContext hotForm = FixtureContext.builder()
                .fixture(base.getFixture())
                .homeTeam(base.getHomeTeam())
                .awayTeam(base.getAwayTeam())
                .homeTeamStats(base.getHomeTeamStats())
                .awayTeamStats(base.getAwayTeamStats())
                .homeTeamForm(TeamRecentForm.builder().teamId(1L).scoredAvgHome(3.2).build())
                .awayTeamForm(TeamRecentForm.builder().teamId(2L).concededAvgAway(2.8).build())
                .build();

        assertThat(engine.estimate(hotForm, true).expectedGoals())
                .isGreaterThan(engine.estimate(base, true).expectedGoals());
    }

    private static TeamSeasonStats homeSide(int matches, int goals, int conceded, int failedToScore,
            int cleanSheets, double xgFor, double xgAgainst) {
        return TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(matches * 2)
                .matchesPlayedHome(matches)
                .seasonGoalsHome(goals)
                .seasonConcededHome(conceded)
                .seasonFailedToScoreHome(failedToScore)
                .seasonCleanSheetsHome(cleanSheets)
                .xgForAvgHome(xgFor)
                .xgAgainstAvgHome(xgAgainst)
                .build();
    }

    private static TeamSeasonStats awaySide(int matches, int goals, int conceded, int failedToScore,
            int cleanSheets, double xgFor, double xgAgainst) {
        return TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(matches * 2)
                .matchesPlayedAway(matches)
                .seasonGoalsAway(goals)
                .seasonConcededAway(conceded)
                .seasonFailedToScoreAway(failedToScore)
                .seasonCleanSheetsAway(cleanSheets)
                .xgForAvgAway(xgFor)
                .xgAgainstAvgAway(xgAgainst)
                .build();
    }

    private static FixtureContext context(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
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
                .build();
    }
}
