package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Over15GoalsRecommendationEngineTest {

    private Over15GoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new Over15GoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns OVER_15_GOALS")
    void getType_returnsOver15Goals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_15_GOALS);
    }

    @Test
    @DisplayName("analyze recommends Over 1.5 Goals for high-scoring sides with strong O1.5 rates")
    void analyze_withHighOver15Rates_returnsOver15Market() {
        Optional<Recommendation> result = engine.analyze(highScoringContext());

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_15_GOALS);
        assertThat(result.get().getMarket()).isEqualTo("Over 1.5 Goals");
        assertThat(result.get().getOdds()).isEqualTo(1.28);
        assertThat(result.get().getFactors()).containsKeys("expectedGoals", "over15PctHome", "over15PctAway", "apiO15Potential");
    }

    @Test
    @DisplayName("analyze with low expected goals returns empty")
    void analyze_withLowExpectedGoals_returnsEmpty() {
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(0.5, 0.3, 0.4, 0.5, 80.0, 80.0));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze still recommends when Over 1.5 rates are modest if expected goals are high")
    void analyze_withModestOver15Rates_stillRecommends() {
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(2.5, 1.8, 1.0, 1.4, 55.0, 55.0));

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Over 1.5 Goals");
    }

    @Test
    @DisplayName("analyze with incomplete data returns empty")
    void analyze_withIncompleteData_returnsEmpty() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze blends form Over 1.5 percentages when last-x data is present")
    void analyze_withForm_tracksFormOver15() {
        Optional<Recommendation> result = engine.analyze(highScoringContextWithForm());

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("formDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKeys("homeOverFormPct", "awayOverFormPct");
    }

    private FixtureContext highScoringContext() {
        return contextWithGoalStats(2.5, 1.8, 1.0, 1.4, 82.0, 78.0);
    }

    private FixtureContext highScoringContextWithForm() {
        FixtureContext base = highScoringContext();
        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .scoredAvgHome(2.8)
                .scoredAvgAway(2.0)
                .concededAvgHome(1.0)
                .concededAvgAway(1.4)
                .over15PercentageOverall(90.0)
                .build();
        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .scoredAvgHome(2.2)
                .scoredAvgAway(2.0)
                .concededAvgHome(1.2)
                .concededAvgAway(1.6)
                .over15PercentageOverall(85.0)
                .build();
        return FixtureContext.builder()
                .fixture(base.getFixture())
                .homeTeam(base.getHomeTeam())
                .awayTeam(base.getAwayTeam())
                .homeTeamStats(base.getHomeTeamStats())
                .awayTeamStats(base.getAwayTeamStats())
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .odds(base.getOdds())
                .potentials(base.getPotentials())
                .build();
    }

    private FixtureContext contextWithGoalStats(
            double homeScored, double awayScored, double homeConceded, double awayConceded,
            double homeOver15, double awayOver15) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver15PercentageOverall(homeOver15)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver15PercentageOverall(awayOver15)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver15(1.28)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o15Potential(80.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .potentials(potentials)
                .build();
    }

    private Fixture createFixture() {
        return Fixture.builder()
                .id(1000L)
                .seasonId(100L)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .homeTeamName("Home Team")
                .awayTeamName("Away Team")
                .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                .status("incomplete")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
