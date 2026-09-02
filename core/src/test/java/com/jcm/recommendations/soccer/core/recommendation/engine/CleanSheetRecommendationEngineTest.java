package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CleanSheetRecommendationEngineTest {

    private CleanSheetRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CleanSheetRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns CLEAN_SHEET")
    void getType_returnsCleanSheet() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.CLEAN_SHEET);
    }

    @Test
    @DisplayName("analyze with strong defensive team returns recommendation")
    void analyze_withStrongDefensiveTeam_returnsRecommendation() {
        FixtureContext context = createStrongDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.CLEAN_SHEET);
        assertThat(result.get().getMarket()).contains("Clean Sheet");
    }

    @Test
    @DisplayName("analyze picks the team with better defensive record")
    void analyze_picksBetterDefensiveTeam() {
        FixtureContext context = createStrongDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).contains("Strong Defense");
    }

    @Test
    @DisplayName("analyze with poor defensive teams returns empty")
    void analyze_withPoorDefensiveTeams_returnsEmpty() {
        FixtureContext context = createPoorDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze includes team factors")
    void analyze_includesTeamFactors() {
        FixtureContext context = createStrongDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("team");
        assertThat(result.get().getFactors()).containsKey("isHomeTeam");
        assertThat(result.get().getFactors()).containsKey("teamCleanSheetSeasonPct");
    }

    @Test
    @DisplayName("analyze considers opponent failed to score rate")
    void analyze_considersOpponentFailedToScoreRate() {
        FixtureContext context = createStrongDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("opponentFailedToScoreSeasonPct");
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
    @DisplayName("isApplicable returns true with complete data")
    void isApplicable_withCompleteData_returnsTrue() {
        FixtureContext context = createStrongDefensiveContext();

        assertThat(engine.isApplicable(context)).isTrue();
    }

    @Test
    @DisplayName("analyze integrates xG data when available")
    void analyze_integratesXgData() {
        FixtureContext context = createContextWithXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("teamXgaPerGame");
        assertThat(result.get().getFactors()).containsKey("teamDefensiveXgRating");
        assertThat(result.get().getFactors()).containsKey("opponentXgPerGame");
        assertThat(result.get().getFactors()).containsKey("opponentAttackingXgRating");
    }

    @Test
    @DisplayName("analyze detects hot defensive streak")
    void analyze_detectsHotDefensiveStreak() {
        FixtureContext context = createContextWithHotStreak();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("hotDefensiveStreak")).isEqualTo(true);
        assertThat(result.get().getDescription()).contains("hot streak");
    }

    @Test
    @DisplayName("published score is the Poisson probability of the opponent being shut out")
    void analyze_publishesPoissonProbabilityNotAnIndex() {
        FixtureContext context = createStrongDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        double lambda = (double) result.get().getFactors().get("opponentExpectedGoals");
        assertThat(result.get().getScore())
                .as("score must be exp(-lambda), not a weighted index")
                .isCloseTo(Math.exp(-lambda) * 100.0, within(0.01));
    }

    @Test
    @DisplayName("streaks and opponent weakness are reported but do not scale the score")
    void analyze_contextualSignalsDoNotInflateScore() {
        FixtureContext context = createContextWithHotStreak();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        Map<String, Object> factors = result.get().getFactors();
        assertThat(factors.get("hotDefensiveStreak")).isEqualTo(true);
        assertThat(factors)
                .as("the old multiplier stack must be gone")
                .doesNotContainKeys("defensiveRatingMultiplier", "opponentWeaknessMultiplier", "xgRegressionRisk");

        double lambda = (double) factors.get("opponentExpectedGoals");
        assertThat(result.get().getScore()).isCloseTo(Math.exp(-lambda) * 100.0, within(0.01));
    }

    @Test
    @DisplayName("scores stay in a realistic band for the clean sheet market")
    void analyze_scoresStayRealistic() {
        for (FixtureContext context : List.of(
                createStrongDefensiveContext(), createContextWithXgData(), createContextWithHotStreak())) {
            Optional<Recommendation> result = engine.analyze(context);
            assertThat(result).isPresent();
            assertThat(result.get().getScore())
                    .as("clean sheets happen in ~32%% of home matches; nothing should approach certainty")
                    .isGreaterThan(30.0)
                    .isLessThan(75.0);
        }
    }

    @Test
    @DisplayName("an average fixture lands near the base rate and is not published")
    void analyze_averageFixtureIsNotPublished() {
        assertThat(engine.analyze(createLeagueAverageContext())).isEmpty();
    }

    @Test
    @DisplayName("a thin venue record is not scored")
    void analyze_thinVenueRecord_returnsEmpty() {
        assertThat(engine.analyze(createThinVenueRecordContext())).isEmpty();
    }

    @Test
    @DisplayName("analyze tracks risk flags")
    void analyze_tracksRiskFlags() {
        FixtureContext context = createStrongDefensiveContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
    }

    @Test
    @DisplayName("xG pulls the expected goals toward the underlying numbers")
    void analyze_xgTempersAnOverperformingDefence() {
        // Conceding 0.3 at home on a 1.00 xGA: the xG term should hold the estimate above what the
        // raw conceded rate alone would imply, rather than the old model's regression multiplier.
        Optional<Recommendation> result = engine.analyze(createContextWithXgRegressionRisk());

        assertThat(result).isPresent();
        Map<String, Object> factors = result.get().getFactors();
        double seasonLambda = (double) factors.get("seasonExpectedGoals");
        double xgLambda = (double) factors.get("xgExpectedGoals");
        double blended = (double) factors.get("opponentExpectedGoals");

        assertThat(xgLambda).isGreaterThan(seasonLambda);
        assertThat(blended).isBetween(seasonLambda, xgLambda);
    }

    // Helper methods

    private FixtureContext createStrongDefensiveContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(8)  // 80% at home (8/10)
                .seasonCleanSheetsAway(4)
                .seasonCleanSheetsOverall(12)
                .seasonConcededHome(5)  // 0.5 per game
                .seasonConcededAway(10)
                .seasonFailedToScoreHome(1)
                .seasonFailedToScoreAway(2)
                .seasonFailedToScoreOverall(3)
                .seasonGoalsHome(20)
                .seasonGoalsAway(15)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(3)
                .seasonCleanSheetsAway(2)  // 20% away
                .seasonCleanSheetsOverall(5)
                .seasonConcededHome(12)
                .seasonConcededAway(15)
                .seasonFailedToScoreHome(3)  // 30% FTS home - good for home CS
                .seasonFailedToScoreAway(4)
                .seasonFailedToScoreOverall(7)
                .seasonGoalsHome(18)
                .seasonGoalsAway(10)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Strong Defense"))
                .awayTeam(createTeam(2L, "Weak Attack"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createPoorDefensiveContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(1)  // 10%
                .seasonCleanSheetsAway(0)
                .seasonCleanSheetsOverall(1)
                .seasonConcededHome(20)  // 2.0 per game
                .seasonConcededAway(25)
                .seasonFailedToScoreHome(1)
                .seasonFailedToScoreAway(1)
                .seasonFailedToScoreOverall(2)
                .seasonGoalsHome(15)
                .seasonGoalsAway(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(1)
                .seasonCleanSheetsAway(1)  // 10%
                .seasonCleanSheetsOverall(2)
                .seasonConcededHome(18)
                .seasonConcededAway(22)
                .seasonFailedToScoreHome(1)  // Low FTS - strong attack
                .seasonFailedToScoreAway(1)
                .seasonFailedToScoreOverall(2)
                .seasonGoalsHome(25)
                .seasonGoalsAway(20)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Poor Defense Home"))
                .awayTeam(createTeam(2L, "Poor Defense Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(7)
                .seasonCleanSheetsAway(4)
                .seasonCleanSheetsOverall(11)
                .seasonConcededHome(6)
                .seasonConcededAway(10)
                .seasonFailedToScoreHome(2)
                .seasonFailedToScoreAway(3)
                .seasonFailedToScoreOverall(5)
                .seasonGoalsHome(18)
                .seasonGoalsAway(12)
                .xgAgainstAvgHome(0.70)  // Elite xGA
                .xgAgainstAvgAway(1.00)
                .xgAgainstAvgOverall(0.85)
                .xgForAvgHome(1.50)
                .xgForAvgAway(1.20)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(3)
                .seasonCleanSheetsAway(2)
                .seasonCleanSheetsOverall(5)
                .seasonConcededHome(12)
                .seasonConcededAway(15)
                .seasonFailedToScoreHome(4)
                .seasonFailedToScoreAway(5)
                .seasonFailedToScoreOverall(9)
                .seasonGoalsHome(15)
                .seasonGoalsAway(8)
                .xgForAvgHome(0.75)  // Poor xG
                .xgForAvgAway(0.60)
                .xgForAvgOverall(0.67)
                .xgAgainstAvgHome(1.30)
                .xgAgainstAvgAway(1.50)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "xG Elite Defense"))
                .awayTeam(createTeam(2L, "xG Poor Attack"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithHotStreak() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(6)
                .seasonCleanSheetsAway(4)
                .seasonCleanSheetsOverall(10)
                .seasonConcededHome(8)
                .seasonConcededAway(12)
                .seasonFailedToScoreHome(2)
                .seasonFailedToScoreAway(3)
                .seasonFailedToScoreOverall(5)
                .seasonGoalsHome(20)
                .seasonGoalsAway(12)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(2)
                .seasonCleanSheetsAway(1)
                .seasonCleanSheetsOverall(3)
                .seasonConcededHome(15)
                .seasonConcededAway(18)
                .seasonFailedToScoreHome(4)
                .seasonFailedToScoreAway(5)
                .seasonFailedToScoreOverall(9)
                .seasonGoalsHome(12)
                .seasonGoalsAway(8)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cleanSheetsHome(4)  // 4 of 5 = hot streak
                .cleanSheetsAway(2)
                .cleanSheetsOverall(6)
                .scoredAvgHome(2.0)
                .scoredAvgAway(1.2)
                .concededAvgHome(0.2)
                .concededAvgAway(0.8)
                .failedToScoreHome(0)
                .failedToScoreAway(1)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cleanSheetsHome(1)
                .cleanSheetsAway(0)
                .cleanSheetsOverall(1)
                .scoredAvgHome(0.8)
                .scoredAvgAway(0.4)
                .concededAvgHome(1.5)
                .concededAvgAway(2.0)
                .failedToScoreHome(2)  // High FTS
                .failedToScoreAway(3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Hot Streak Team"))
                .awayTeam(createTeam(2L, "Poor Form Attack"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createContextWithXgRegressionRisk() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(8)  // High CS
                .seasonCleanSheetsAway(5)
                .seasonCleanSheetsOverall(13)
                .seasonConcededHome(3)  // 0.3 per game - much lower than xGA
                .seasonConcededAway(8)
                .seasonFailedToScoreHome(1)
                .seasonFailedToScoreAway(2)
                .seasonFailedToScoreOverall(3)
                .seasonGoalsHome(22)
                .seasonGoalsAway(14)
                .xgAgainstAvgHome(1.00)  // xGA much higher than actual conceded
                .xgAgainstAvgAway(1.20)
                .xgAgainstAvgOverall(1.10)
                .xgForAvgHome(1.60)
                .xgForAvgAway(1.20)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonCleanSheetsHome(2)
                .seasonCleanSheetsAway(1)
                .seasonCleanSheetsOverall(3)
                .seasonConcededHome(14)
                .seasonConcededAway(16)
                .seasonFailedToScoreHome(4)
                .seasonFailedToScoreAway(5)
                .seasonFailedToScoreOverall(9)
                .seasonGoalsHome(12)
                .seasonGoalsAway(8)
                .xgForAvgHome(0.90)
                .xgForAvgAway(0.70)
                .xgForAvgOverall(0.80)
                .xgAgainstAvgHome(1.40)
                .xgAgainstAvgAway(1.60)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Regression Risk Team"))
                .awayTeam(createTeam(2L, "Weak Attack"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    /** Both sides at the measured league rates: 1.54 scored at home, 1.23 away, over 19 matches. */
    private FixtureContext createLeagueAverageContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(38)
                .matchesPlayedHome(19)
                .matchesPlayedAway(19)
                .seasonCleanSheetsHome(6)
                .seasonCleanSheetsAway(4)
                .seasonCleanSheetsOverall(10)
                .seasonGoalsHome(29)
                .seasonGoalsAway(23)
                .seasonConcededHome(23)
                .seasonConcededAway(29)
                .seasonFailedToScoreHome(4)
                .seasonFailedToScoreAway(6)
                .seasonFailedToScoreOverall(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(38)
                .matchesPlayedHome(19)
                .matchesPlayedAway(19)
                .seasonCleanSheetsHome(6)
                .seasonCleanSheetsAway(4)
                .seasonCleanSheetsOverall(10)
                .seasonGoalsHome(29)
                .seasonGoalsAway(23)
                .seasonConcededHome(23)
                .seasonConcededAway(29)
                .seasonFailedToScoreHome(4)
                .seasonFailedToScoreAway(6)
                .seasonFailedToScoreOverall(10)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Average Home"))
                .awayTeam(createTeam(2L, "Average Away"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    /** A defensive record good enough to score well, on too few matches to believe. */
    private FixtureContext createThinVenueRecordContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(6)
                .matchesPlayedHome(3)
                .matchesPlayedAway(3)
                .seasonCleanSheetsHome(3)
                .seasonCleanSheetsAway(1)
                .seasonCleanSheetsOverall(4)
                .seasonGoalsHome(9)
                .seasonGoalsAway(3)
                .seasonConcededHome(0)
                .seasonConcededAway(4)
                .seasonFailedToScoreHome(0)
                .seasonFailedToScoreAway(1)
                .seasonFailedToScoreOverall(1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(6)
                .matchesPlayedHome(3)
                .matchesPlayedAway(3)
                .seasonCleanSheetsHome(1)
                .seasonCleanSheetsAway(0)
                .seasonCleanSheetsOverall(1)
                .seasonGoalsHome(4)
                .seasonGoalsAway(1)
                .seasonConcededHome(5)
                .seasonConcededAway(8)
                .seasonFailedToScoreHome(1)
                .seasonFailedToScoreAway(2)
                .seasonFailedToScoreOverall(3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Thin Record"))
                .awayTeam(createTeam(2L, "Thin Opponent"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
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
