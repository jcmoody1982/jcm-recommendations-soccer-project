package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
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
    @DisplayName("analyze treats 0-1 Over 1.5 rates as percentages")
    void analyze_withUnitIntervalOver15Rates_recommends() {
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(1.5, 1.3, 1.2, 1.4, 0.78, 0.74));

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_15_GOALS);
        assertThat((Double) result.get().getFactors().get("over15PctHome")).isGreaterThan(50.0);
    }

    @Test
    @DisplayName("analyze derives Over 1.5 rate from match counts when percentage is missing")
    void analyze_withOver15CountsOnly_recommends() {
        Optional<Recommendation> result = engine.analyze(contextWithOver15Counts(1.5, 1.3, 1.2, 1.4, 8, 7));

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Over 1.5 Goals");
    }

    @Test
    @DisplayName("analyzeAll skips a fixture that throws and still scores the rest")
    void analyzeAll_skipsThrowingFixture() {
        FixtureContext good = highScoringContext();
        FixtureContext bad = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .homeTeamStats(null)
                .awayTeamStats(null)
                .build();
        RecommendationEngine throwing = new RecommendationEngine() {
            @Override
            public RecommendationType getType() {
                return RecommendationType.OVER_15_GOALS;
            }

            @Override
            public Optional<Recommendation> analyze(FixtureContext context) {
                if (context.getHomeTeamStats() == null) {
                    throw new NullPointerException("incomplete fixture");
                }
                return engine.analyze(context);
            }
        };

        assertThat(throwing.analyzeAll(java.util.List.of(bad, good))).hasSize(1);
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

    @Test
    @DisplayName("analyze never saturates at 100 even for a goal-fest with perfect over rates")
    void analyze_extremeFixture_staysBelowCertainty() {
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(4.0, 3.5, 3.0, 3.2, 100.0, 100.0));

        assertThat(result).isPresent();
        // The old additive scoring stacked a high-scoring boost, an xG boost and an
        // expected-goals lift on an already-high index, hit the 100 clamp, and tied every busy
        // fixture at exactly 100 — which handed the Elite board to whichever had the shortest price.
        assertThat(result.get().getScore()).isLessThan(97.0);
        assertThat(result.get().getScore()).isGreaterThan(85.0);
    }

    @Test
    @DisplayName("analyze keeps busier fixtures separable at the top of the range")
    void analyze_higherExpectedGoals_scoresStrictlyHigher() {
        double busy = scoreFor(contextWithGoalStats(3.2, 2.8, 2.4, 2.6, 95.0, 95.0));
        double busier = scoreFor(contextWithGoalStats(4.0, 3.5, 3.0, 3.2, 95.0, 95.0));

        assertThat(busier).isGreaterThan(busy);
    }

    @Test
    @DisplayName("analyze reports the Poisson and empirical halves of the estimate")
    void analyze_exposesBothEstimates() {
        Optional<Recommendation> result = engine.analyze(highScoringContext());

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKeys("poissonProbability", "empiricalOverPct");
        assertThat(result.get().getFactors()).containsEntry("goalsNeeded", 2);
        // The published score sits between the two inputs it is blended from.
        double poisson = (double) result.get().getFactors().get("poissonProbability");
        double empirical = (double) result.get().getFactors().get("empiricalOverPct");
        assertThat(result.get().getScore())
                .isBetween(Math.min(poisson, empirical), Math.max(poisson, empirical));
    }

    @Test
    @DisplayName("analyze drops a fixture that only matches the league average")
    void analyze_averageFixture_isNotPublished() {
        // Around 2.4 expected goals is close to a typical fixture. Over 1.5 clears in roughly
        // three quarters of all matches, so an average fixture is not a recommendation.
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(1.3, 1.1, 1.1, 1.2, 68.0, 66.0));

        assertThat(result).isEmpty();
    }

    private double scoreFor(FixtureContext context) {
        Optional<Recommendation> result = engine.analyze(context);
        assertThat(result).isPresent();
        return result.get().getScore();
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

    private FixtureContext contextWithOver15Counts(
            double homeScored, double awayScored, double homeConceded, double awayConceded,
            int homeOver15, int awayOver15) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver15Overall(homeOver15)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver15Overall(awayOver15)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver15(1.28)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o15Potential(0.72)
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
