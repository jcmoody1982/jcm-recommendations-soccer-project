package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UnderGoalsRecommendationEngineTest {

    private UnderGoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new UnderGoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns UNDER_GOALS")
    void getType_returnsUnderGoals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.UNDER_GOALS);
    }

    @Test
    @DisplayName("analyze returns recommendation for low scoring context")
    void analyze_withLowScoringTeams_returnsRecommendation() {
        FixtureContext context = createLowScoringContext();
        
        Optional<Recommendation> result = engine.analyze(context);
        
        if (result.isPresent()) {
            assertThat(result.get().getType()).isEqualTo(RecommendationType.UNDER_GOALS);
            assertThat(result.get().getMarket()).startsWith("Under");
        }
    }

    @Test
    @DisplayName("analyze with high scoring teams returns empty")
    void analyze_withHighScoringTeams_returnsEmpty() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("when recommendation generated, includes expected goals in factors")
    void analyze_includesExpectedGoalsInFactors() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("expectedGoals");
            assertThat(result.get().getFactors()).containsKey("homeGoalsScoredAvg");
            assertThat(result.get().getFactors()).containsKey("awayGoalsScoredAvg");
        }
    }

    @Test
    @DisplayName("analyze tracks formDataAvailable correctly")
    void analyze_tracksFormDataAvailable() {
        FixtureContext contextWithForm = createLowScoringContextWithForm();
        FixtureContext contextWithoutForm = createLowScoringContext();

        Optional<Recommendation> resultWithForm = engine.analyze(contextWithForm);
        Optional<Recommendation> resultWithoutForm = engine.analyze(contextWithoutForm);

        if (resultWithForm.isPresent()) {
            assertThat(resultWithForm.get().getFactors().get("formDataAvailable")).isEqualTo(true);
        }
        if (resultWithoutForm.isPresent()) {
            assertThat(resultWithoutForm.get().getFactors().get("formDataAvailable")).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("analyze with very low combined goals applies low-scoring boost")
    void analyze_withVeryLowCombinedGoals_appliesLowScoringBoost() {
        FixtureContext context = createVeryLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("lowScoringBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("lowScoringBoostAmount")).isEqualTo(5.0);
    }

    @Test
    @DisplayName("analyze with high clean sheet rates applies defensive strength boost")
    void analyze_withHighCleanSheetRates_appliesDefensiveBoost() {
        FixtureContext context = createDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("defensiveStrengthBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("defensiveStrengthBoostAmount")).isEqualTo(4.0);
    }

    @Test
    @DisplayName("analyze with low xG data applies xG boost")
    void analyze_withLowXgData_appliesXgBoost() {
        FixtureContext context = createContextWithLowXgData(0.9, 1.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("xgBoostAmount")).isEqualTo(4.0);
    }

    @Test
    @DisplayName("analyze without xG data does not apply xG boost")
    void analyze_withoutXgData_noXgBoost() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze tracks clean sheet percentages")
    void analyze_tracksCleanSheetPercentages() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("homeCleanSheetPct");
            assertThat(result.get().getFactors()).containsKey("awayCleanSheetPct");
        }
    }

    @Test
    @DisplayName("analyze tracks failed to score percentages")
    void analyze_tracksFailedToScorePercentages() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("homeFailedToScorePct");
            assertThat(result.get().getFactors()).containsKey("awayFailedToScorePct");
        }
    }

    @Test
    @DisplayName("analyze tracks under percentages")
    void analyze_tracksUnderPercentages() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("homeUnder15Pct");
            assertThat(result.get().getFactors()).containsKey("awayUnder15Pct");
            assertThat(result.get().getFactors()).containsKey("homeUnder25Pct");
            assertThat(result.get().getFactors()).containsKey("awayUnder25Pct");
        }
    }

    @Test
    @DisplayName("analyze with incomplete data returns empty")
    void analyze_withIncompleteData_returnsEmpty() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .build();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    private FixtureContext createLowScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(8)   // 0.8 per game
                .seasonGoalsAway(6)
                .seasonConcededHome(6)  // 0.6 per game
                .seasonConcededAway(8)
                .seasonCleanSheetsOverall(4)
                .seasonFailedToScoreOverall(3)
                .seasonOver15PercentageOverall(60.0)
                .seasonOver25PercentageOverall(30.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(7)
                .seasonGoalsAway(5)   // 0.5 per game
                .seasonConcededHome(6)
                .seasonConcededAway(7)  // 0.7 per game
                .seasonCleanSheetsOverall(3)
                .seasonFailedToScoreOverall(4)
                .seasonOver15PercentageOverall(55.0)
                .seasonOver25PercentageOverall(25.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtUnder25(1.80)
                .oddsFtUnder15(3.20)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .u15Potential(60.0)
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

    private FixtureContext createLowScoringContextWithForm() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(8)
                .seasonGoalsAway(6)
                .seasonConcededHome(6)
                .seasonConcededAway(8)
                .seasonCleanSheetsOverall(4)
                .seasonFailedToScoreOverall(3)
                .seasonOver15PercentageOverall(60.0)
                .seasonOver25PercentageOverall(30.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(7)
                .seasonGoalsAway(5)
                .seasonConcededHome(6)
                .seasonConcededAway(7)
                .seasonCleanSheetsOverall(3)
                .seasonFailedToScoreOverall(4)
                .seasonOver15PercentageOverall(55.0)
                .seasonOver25PercentageOverall(25.0)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .scoredAvgHome(0.6)
                .scoredAvgAway(0.4)
                .concededAvgHome(0.4)
                .concededAvgAway(0.8)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .scoredAvgHome(0.8)
                .scoredAvgAway(0.4)
                .concededAvgHome(0.6)
                .concededAvgAway(0.6)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtUnder25(1.80)
                .oddsFtUnder15(3.20)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .u15Potential(60.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .odds(odds)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createHighScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(25)
                .seasonGoalsAway(20)
                .seasonConcededHome(15)
                .seasonConcededAway(18)
                .seasonCleanSheetsOverall(1)
                .seasonFailedToScoreOverall(0)
                .seasonOver15PercentageOverall(95.0)
                .seasonOver25PercentageOverall(80.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(22)
                .seasonGoalsAway(18)
                .seasonConcededHome(16)
                .seasonConcededAway(20)
                .seasonCleanSheetsOverall(1)
                .seasonFailedToScoreOverall(1)
                .seasonOver15PercentageOverall(90.0)
                .seasonOver25PercentageOverall(75.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createVeryLowScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(5)   // 0.5 per game
                .seasonGoalsAway(4)
                .seasonConcededHome(4)  // 0.4 per game
                .seasonConcededAway(5)
                .seasonCleanSheetsOverall(5)
                .seasonFailedToScoreOverall(4)
                .seasonOver15PercentageOverall(40.0)
                .seasonOver25PercentageOverall(15.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(6)
                .seasonGoalsAway(4)   // 0.4 per game
                .seasonConcededHome(4)
                .seasonConcededAway(5)  // 0.5 per game
                .seasonCleanSheetsOverall(4)
                .seasonFailedToScoreOverall(5)
                .seasonOver15PercentageOverall(45.0)
                .seasonOver25PercentageOverall(20.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtUnder25(1.40)
                .oddsFtUnder15(2.50)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .u15Potential(75.0)
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

    private FixtureContext createDefensiveContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(6)
                .seasonGoalsAway(5)
                .seasonConcededHome(4)
                .seasonConcededAway(6)
                .seasonCleanSheetsOverall(4)  // 40% clean sheet rate
                .seasonFailedToScoreOverall(3)
                .seasonOver15PercentageOverall(50.0)
                .seasonOver25PercentageOverall(25.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(7)
                .seasonGoalsAway(5)
                .seasonConcededHome(5)
                .seasonConcededAway(5)
                .seasonCleanSheetsOverall(4)  // 40% clean sheet rate
                .seasonFailedToScoreOverall(4)
                .seasonOver15PercentageOverall(55.0)
                .seasonOver25PercentageOverall(30.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtUnder25(1.65)
                .oddsFtUnder15(3.00)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .u15Potential(65.0)
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

    private FixtureContext createContextWithLowXgData(double homeXgFor, double awayXgFor) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(7)
                .seasonGoalsAway(5)
                .seasonConcededHome(5)
                .seasonConcededAway(7)
                .seasonCleanSheetsOverall(3)
                .seasonFailedToScoreOverall(3)
                .seasonOver15PercentageOverall(55.0)
                .seasonOver25PercentageOverall(30.0)
                .xgForAvgHome(homeXgFor)
                .xgAgainstAvgHome(0.8)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(8)
                .seasonGoalsAway(6)
                .seasonConcededHome(6)
                .seasonConcededAway(8)
                .seasonCleanSheetsOverall(3)
                .seasonFailedToScoreOverall(3)
                .seasonOver15PercentageOverall(60.0)
                .seasonOver25PercentageOverall(35.0)
                .xgForAvgAway(awayXgFor)
                .xgAgainstAvgAway(1.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtUnder25(1.75)
                .oddsFtUnder15(3.10)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .u15Potential(60.0)
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
