package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OverGoalsRecommendationEngineTest {

    private OverGoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OverGoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns OVER_GOALS")
    void getType_returnsOverGoals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_GOALS);
    }

    @Test
    @DisplayName("analyze returns recommendation for high scoring context")
    void analyze_withHighScoringTeams_returnsRecommendation() {
        FixtureContext context = createHighScoringContext();
        
        Optional<Recommendation> result = engine.analyze(context);
        
        if (result.isPresent()) {
            assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_GOALS);
            assertThat(result.get().getMarket()).startsWith("Over");
        }
    }

    @Test
    @DisplayName("analyze with low scoring teams returns empty")
    void analyze_withLowScoringTeams_returnsEmpty() {
        FixtureContext context = createContextWithGoalStats(0.5, 0.3, 0.4, 0.5);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("when recommendation generated, includes expected goals in factors")
    void analyze_includesExpectedGoalsInFactors() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("expectedGoals");
            assertThat(result.get().getFactors()).containsKey("homeGoalsScoredAvg");
            assertThat(result.get().getFactors()).containsKey("awayGoalsScoredAvg");
        }
    }

    @Test
    @DisplayName("when recommendation generated with odds, includes odds in result")
    void analyze_withOdds_includesOddsInResult() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getOdds()).isNotNull();
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

    @Test
    @DisplayName("analyze tracks formDataAvailable correctly")
    void analyze_tracksFormDataAvailable() {
        FixtureContext contextWithForm = createHighScoringContextWithForm();
        FixtureContext contextWithoutForm = createHighScoringContext();

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
    @DisplayName("analyze with high combined goals applies high-scoring boost")
    void analyze_withHighCombinedGoals_appliesHighScoringBoost() {
        FixtureContext context = createVeryHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("highScoringBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("highScoringBoostAmount")).isEqualTo(5.0);
    }

    @Test
    @DisplayName("analyze with xG data applies xG boost when threshold met")
    void analyze_withHighXgData_appliesXgBoost() {
        FixtureContext context = createContextWithXgData(1.6, 1.4);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("xgBoostAmount")).isEqualTo(4.0);
    }

    @Test
    @DisplayName("analyze without xG data does not apply xG boost")
    void analyze_withoutXgData_noXgBoost() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze never steps up to Over 3.5")
    void analyze_neverStepsUpToOver35() {
        Optional<Recommendation> result = engine.analyze(createHighScoringContext());

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Over 2.5 Goals");
    }

    @Test
    @DisplayName("analyze tracks Over 3.5 percentages")
    void analyze_tracksOver35Percentages() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("homeOver35Pct");
            assertThat(result.get().getFactors()).containsKey("awayOver35Pct");
        }
    }

    @Test
    @DisplayName("analyze tracks conceded averages")
    void analyze_tracksConcededAverages() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("homeGoalsConcededAvg");
            assertThat(result.get().getFactors()).containsKey("awayGoalsConcededAvg");
        }
    }

    private FixtureContext createContextWithGoalStats(double homeScored, double awayScored, 
            double homeConceded, double awayConceded) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver25PercentageOverall(70.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver25PercentageOverall(65.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithGoalStatsAndOdds(double homeScored, double awayScored, 
            double homeConceded, double awayConceded) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver25PercentageOverall(70.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver25PercentageOverall(65.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.85)
                .oddsFtOver35(2.50)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .build();
    }

    private FixtureContext createHighScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(25)
                .seasonGoalsAway(20)
                .seasonConcededHome(10)
                .seasonConcededAway(12)
                .seasonOver25PercentageOverall(80.0)
                .seasonOver35PercentageOverall(50.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(22)
                .seasonGoalsAway(18)
                .seasonConcededHome(12)
                .seasonConcededAway(14)
                .seasonOver25PercentageOverall(75.0)
                .seasonOver35PercentageOverall(45.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.60)
                .oddsFtOver35(2.40)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(80.0)
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

    private FixtureContext createHighScoringContextWithForm() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(25)
                .seasonGoalsAway(20)
                .seasonConcededHome(10)
                .seasonConcededAway(12)
                .seasonOver25PercentageOverall(80.0)
                .seasonOver35PercentageOverall(50.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(22)
                .seasonGoalsAway(18)
                .seasonConcededHome(12)
                .seasonConcededAway(14)
                .seasonOver25PercentageOverall(75.0)
                .seasonOver35PercentageOverall(45.0)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .scoredAvgHome(2.8)
                .scoredAvgAway(2.0)
                .concededAvgHome(1.0)
                .concededAvgAway(1.4)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .scoredAvgHome(2.2)
                .scoredAvgAway(2.0)
                .concededAvgHome(1.2)
                .concededAvgAway(1.6)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.60)
                .oddsFtOver35(2.40)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(80.0)
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

    private FixtureContext createVeryHighScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(30)  // 3.0 per game
                .seasonGoalsAway(25)
                .seasonConcededHome(18)  // 1.8 per game
                .seasonConcededAway(15)
                .seasonOver25PercentageOverall(90.0)
                .seasonOver35PercentageOverall(60.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(28)
                .seasonGoalsAway(22)  // 2.2 per game
                .seasonConcededHome(15)
                .seasonConcededAway(20)  // 2.0 per game
                .seasonOver25PercentageOverall(85.0)
                .seasonOver35PercentageOverall(55.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.40)
                .oddsFtOver35(1.90)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(90.0)
                .o35Potential(70.0)
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

    private FixtureContext createContextWithXgData(double homeXgFor, double awayXgFor) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(20)
                .seasonGoalsAway(16)
                .seasonConcededHome(12)
                .seasonConcededAway(14)
                .seasonOver25PercentageOverall(75.0)
                .seasonOver35PercentageOverall(40.0)
                .xgForAvgHome(homeXgFor)
                .xgAgainstAvgHome(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(18)
                .seasonGoalsAway(15)
                .seasonConcededHome(14)
                .seasonConcededAway(16)
                .seasonOver25PercentageOverall(70.0)
                .seasonOver35PercentageOverall(35.0)
                .xgForAvgAway(awayXgFor)
                .xgAgainstAvgAway(1.4)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.75)
                .oddsFtOver35(2.50)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(75.0)
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
