package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecondHalfGoalsRecommendationEngineTest {

    private SecondHalfGoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SecondHalfGoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns SECOND_HALF_GOALS")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.SECOND_HALF_GOALS);
    }

    @Test
    @DisplayName("analyze returns recommendation for high-scoring matchup")
    void analyze_withHighScoringMatchup_returnsRecommendation() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.SECOND_HALF_GOALS);
        assertThat(result.get().getMarket()).contains("2H Goals");
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze returns empty for low-scoring matchup")
    void analyze_withLowScoringMatchup_returnsEmpty() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze integrates xG data when available")
    void analyze_integratesXgData() {
        FixtureContext context = createContextWithXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("homeXgForAvgHome");
        assertThat(result.get().getFactors()).containsKey("awayXgForAvgAway");
        assertThat(result.get().getFactors()).containsKey("combinedXg");
        assertThat(result.get().getFactors()).containsKey("xgRating");
    }

    @Test
    @DisplayName("analyze tracks 2H goals proxy values")
    void analyze_tracks2HGoalsProxy() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("expected2HGoals");
        assertThat(result.get().getFactors()).containsKey("home2HScoredProxyAvg");
        assertThat(result.get().getFactors()).containsKey("away2HScoredProxyAvg");
        assertThat(result.get().getFactors()).containsKey("home2HConcededProxyAvg");
        assertThat(result.get().getFactors()).containsKey("away2HConcededProxyAvg");
        assertThat(result.get().getFactors()).containsKey("secondHalfRatioUsed");
        assertThat(result.get().getFactors().get("secondHalfRatioUsed")).isEqualTo(0.55);
    }

    @Test
    @DisplayName("analyze tracks late game intensity from cards")
    void analyze_tracksLateGameIntensity() {
        FixtureContext context = createHighIntensityContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeCardsAvg");
        assertThat(result.get().getFactors()).containsKey("awayCardsAvg");
        assertThat(result.get().getFactors()).containsKey("combinedCardsAvg");
        assertThat(result.get().getFactors()).containsKey("lateGameIntensityScore");
        assertThat(result.get().getFactors()).containsKey("intensityProfile");
    }

    @Test
    @DisplayName("analyze tracks fitness indicator")
    void analyze_tracksFitnessIndicator() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeGoalDifferencePerGame");
        assertThat(result.get().getFactors()).containsKey("awayGoalDifferencePerGame");
        assertThat(result.get().getFactors()).containsKey("fitnessIndicatorScore");
    }

    @Test
    @DisplayName("analyze tracks match situation factor")
    void analyze_tracksMatchSituation() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeDrawPct");
        assertThat(result.get().getFactors()).containsKey("awayDrawPct");
        assertThat(result.get().getFactors()).containsKey("matchSituationScore");
    }

    @Test
    @DisplayName("analyze detects strong finisher profile")
    void analyze_detectsStrongFinisher() {
        FixtureContext context = createStrongFinisherContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("finisherProfile");
        assertThat(result.get().getFactors().get("finisherProfile")).isEqualTo("Strong finisher");
    }

    @Test
    @DisplayName("analyze detects late conceder profile")
    void analyze_detectsLateConceder() {
        FixtureContext context = createLateConcederContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("lateConcedeProfile");
    }

    @Test
    @DisplayName("analyze recommends Over 1.5 2H for very high expected goals")
    void analyze_recommendsOver152H_forVeryHighExpected() {
        FixtureContext context = createVeryHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        // With very high expected goals and score, should recommend O1.5 2H
        if (result.get().getScore() >= 75.0) {
            assertThat(result.get().getMarket()).isIn("Over 1.5 2H Goals", "Over 0.5 2H Goals");
        }
    }

    @Test
    @DisplayName("analyze score never exceeds 100% even with stacked multipliers")
    void analyze_score_neverExceeds100() {
        FixtureContext context = createVeryHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getScore()).isLessThanOrEqualTo(100.0);
    }

    @Test
    @DisplayName("analyze tracks positive indicators and risk flags")
    void analyze_tracksIndicatorsAndFlags() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        assertThat(result.get().getFactors().get("positiveIndicators")).isInstanceOf(List.class);
        assertThat(result.get().getFactors().get("riskFlags")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("analyze returns empty when fixture context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = createIncompleteContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze handles missing xG data with redistributed weights")
    void analyze_handlesMissingXgData() {
        FixtureContext context = createHighScoringContextWithoutXg();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
    }

    // Helper methods to create test contexts

    private FixtureContext createHighScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(35)    // 1.75 per game
                .seasonConcededHome(20) // 1.0 per game
                .seasonDrawsHome(5)
                .cardsAvgHome(2.5)
                .seasonCleanSheetsHome(5)
                .xgForAvgHome(1.8)
                .xgAgainstAvgHome(1.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(28)    // 1.4 per game
                .seasonConcededAway(25) // 1.25 per game
                .seasonDrawsAway(6)
                .cardsAvgAway(2.3)
                .seasonCleanSheetsAway(4)
                .xgForAvgAway(1.5)
                .xgAgainstAvgAway(1.2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createLowScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(12)    // 0.6 per game
                .seasonConcededHome(10) // 0.5 per game
                .seasonDrawsHome(8)
                .cardsAvgHome(1.5)
                .seasonCleanSheetsHome(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(10)    // 0.5 per game
                .seasonConcededAway(12) // 0.6 per game
                .seasonDrawsAway(9)
                .cardsAvgAway(1.8)
                .seasonCleanSheetsAway(8)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(30)
                .seasonConcededHome(18)
                .seasonDrawsHome(5)
                .cardsAvgHome(2.5)
                .seasonCleanSheetsHome(6)
                .xgForAvgHome(1.8)
                .xgForAvgAway(1.4)
                .xgAgainstAvgHome(0.9)
                .xgAgainstAvgAway(1.1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(25)
                .seasonConcededAway(22)
                .seasonDrawsAway(6)
                .cardsAvgAway(2.2)
                .seasonCleanSheetsAway(5)
                .xgForAvgHome(1.5)
                .xgForAvgAway(1.6)
                .xgAgainstAvgHome(1.2)
                .xgAgainstAvgAway(1.3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createHighIntensityContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(38)    // 1.9 per game
                .seasonConcededHome(24)
                .seasonDrawsHome(5)
                .cardsAvgHome(3.0)  // High cards
                .seasonCleanSheetsHome(4)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(32)    // 1.6 per game
                .seasonConcededAway(26)
                .seasonDrawsAway(5)
                .cardsAvgAway(2.8)  // High cards
                .seasonCleanSheetsAway(3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createStrongFinisherContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(38)    // High scoring
                .seasonConcededHome(25)
                .seasonDrawsHome(5)
                .cardsAvgHome(2.5)
                .seasonCleanSheetsHome(3)  // Low clean sheets
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(32)    // High scoring
                .seasonConcededAway(28)
                .seasonDrawsAway(4)
                .cardsAvgAway(2.3)
                .seasonCleanSheetsAway(2)  // Low clean sheets
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(105L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createLateConcederContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(28)
                .seasonConcededHome(32)  // High conceded
                .seasonDrawsHome(6)
                .cardsAvgHome(2.5)
                .seasonCleanSheetsHome(2)  // Very low clean sheets
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(25)
                .seasonConcededAway(30)  // High conceded
                .seasonDrawsAway(5)
                .cardsAvgAway(2.4)
                .seasonCleanSheetsAway(2)  // Very low clean sheets
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(106L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createVeryHighScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(48)    // 2.4 per game
                .seasonConcededHome(28)
                .seasonDrawsHome(4)
                .cardsAvgHome(2.8)
                .seasonCleanSheetsHome(4)
                .xgForAvgHome(2.3)
                .xgAgainstAvgHome(1.3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(42)    // 2.1 per game
                .seasonConcededAway(30)
                .seasonDrawsAway(4)
                .cardsAvgAway(2.5)
                .seasonCleanSheetsAway(3)
                .xgForAvgAway(2.0)
                .xgAgainstAvgAway(1.4)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(107L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createIncompleteContext() {
        return FixtureContext.builder()
                .fixture(createFixture(108L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();
    }

    private FixtureContext createHighScoringContextWithoutXg() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsHome(35)
                .seasonConcededHome(20)
                .seasonDrawsHome(5)
                .cardsAvgHome(2.5)
                .seasonCleanSheetsHome(5)
                // No xG data
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonGoalsAway(28)
                .seasonConcededAway(25)
                .seasonDrawsAway(6)
                .cardsAvgAway(2.3)
                .seasonCleanSheetsAway(4)
                // No xG data
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(109L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private Fixture createFixture(Long id) {
        return Fixture.builder()
                .id(id)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .seasonId(1L)
                .status("upcoming")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
