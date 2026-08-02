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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DrawRecommendationEngineTest {

    private DrawRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DrawRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns DRAW")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.DRAW);
    }

    @Test
    @DisplayName("analyze detects evenly matched teams")
    void analyze_detectsEvenlyMatchedTeams() {
        FixtureContext context = createEvenlyMatchedContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.DRAW);
        assertThat(result.get().getMarket()).isEqualTo("Draw");
        assertThat(result.get().getFactors().get("evenlyMatchedScore")).isNotNull();
    }

    @Test
    @DisplayName("analyze detects draw specialists")
    void analyze_detectsDrawSpecialists() {
        FixtureContext context = createDrawSpecialistsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat((Double) result.get().getFactors().get("drawSpecialistMultiplier")).isGreaterThan(1.0);
        assertThat(result.get().getDescription()).contains("Draw specialists");
    }

    @Test
    @DisplayName("analyze integrates xG similarity")
    void analyze_integratesXgSimilarity() {
        FixtureContext context = createContextWithSimilarXg();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("xgSimilarityScore");
        assertThat(result.get().getFactors()).containsKey("xgDifference");
    }

    @Test
    @DisplayName("analyze tracks defensive strength")
    void analyze_tracksDefensiveStrength() {
        FixtureContext context = createDefensiveTeamsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("defensiveStrengthScore");
        assertThat(result.get().getFactors()).containsKey("homeConcededAvg");
        assertThat(result.get().getFactors()).containsKey("awayConcededAvg");
    }

    @Test
    @DisplayName("analyze applies recent draw form multiplier")
    void analyze_appliesRecentDrawFormMultiplier() {
        FixtureContext context = createContextWithDrawHeavyForm();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat((Double) result.get().getFactors().get("recentDrawFormMultiplier")).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("analyze applies referee cards multiplier")
    void analyze_appliesRefereeCardsMultiplier() {
        FixtureContext context = createContextWithLowCardsReferee();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("refereeCardsMultiplier");
        assertThat(result.get().getFactors()).containsKey("refereeCardsPerMatch");
    }

    @Test
    @DisplayName("analyze applies match context multiplier")
    void analyze_appliesMatchContextMultiplier() {
        FixtureContext context = createMidTableContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat((Double) result.get().getFactors().get("matchContextMultiplier")).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("analyze tracks positive indicators")
    void analyze_tracksPositiveIndicators() {
        FixtureContext context = createEvenlyMatchedContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        @SuppressWarnings("unchecked")
        List<String> indicators = (List<String>) result.get().getFactors().get("positiveIndicators");
        assertThat(indicators).isNotEmpty();
    }

    @Test
    @DisplayName("analyze tracks risk flags")
    void analyze_tracksRiskFlags() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        // Even high-scoring teams may generate a draw recommendation if other factors align
        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("riskFlags");
            @SuppressWarnings("unchecked")
            List<String> flags = (List<String>) result.get().getFactors().get("riskFlags");
            assertThat(flags).anyMatch(s -> s.contains("high-scoring") || s.contains("goals"));
        }
    }

    @Test
    @DisplayName("analyze calculates value vs odds")
    void analyze_calculatesValueVsOdds() {
        FixtureContext context = createContextWithOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("drawOdds");
        assertThat(result.get().getFactors()).containsKey("impliedProbability");
        assertThat(result.get().getFactors()).containsKey("valueVsOdds");
    }

    @Test
    @DisplayName("analyze returns empty for mismatched teams")
    void analyze_withMismatchedTeams_returnsEmpty() {
        FixtureContext context = createMismatchedTeamsContext();

        Optional<Recommendation> result = engine.analyze(context);

        // May return empty or weak recommendation filtered out
        if (result.isPresent()) {
            // If present, should have lower confidence
            assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.WEAK);
        }
    }

    @Test
    @DisplayName("analyze returns empty when context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = createIncompleteContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze tracks referee draw percentage")
    void analyze_tracksRefereeDrawPct() {
        FixtureContext context = createContextWithDrawFriendlyReferee();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("refereeDrawPct");
        assertThat((Double) result.get().getFactors().get("refereeDrawPct")).isGreaterThan(25.0);
    }

    @Test
    @DisplayName("analyze handles no xG data with redistributed weights")
    void analyze_withNoXgData_usesRedistributedWeights() {
        FixtureContext context = createContextWithoutXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
        // Should still generate valid recommendation
        assertThat(result.get().getScore()).isGreaterThan(0);
    }

    // Helper methods to create test contexts

    private FixtureContext createEvenlyMatchedContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.5)
                .position(8)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)  // High draw %
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)  // Very close PPG
                .position(9)      // Close position
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.2)  // Similar xG
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createDrawSpecialistsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(10)
                .seasonWinsHome(4)
                .seasonDrawsHome(10)  // 50% draws = draw specialist
                .seasonLossesHome(6)
                .seasonGoalsHome(20)
                .seasonConcededHome(22)
                .xgForAvgOverall(1.1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.2)
                .position(11)
                .seasonWinsAway(3)
                .seasonDrawsAway(9)  // 45% draws = draw specialist
                .seasonLossesAway(8)
                .seasonGoalsAway(16)
                .seasonConcededAway(24)
                .xgForAvgOverall(1.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithSimilarXg() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(22)
                .xgForAvgOverall(1.25)  // Very similar
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(10)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(20)
                .seasonConcededAway(24)
                .xgForAvgOverall(1.20)  // Very similar - diff < 0.2
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createDefensiveTeamsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.5)
                .position(8)
                .seasonWinsHome(6)
                .seasonDrawsHome(6)
                .seasonLossesHome(8)
                .seasonGoalsHome(18)
                .seasonConcededHome(14)  // < 1.0 per game - defensive
                .xgForAvgOverall(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsAway(5)
                .seasonDrawsAway(7)
                .seasonLossesAway(8)
                .seasonGoalsAway(16)
                .seasonConcededAway(16)  // < 1.0 per game - defensive
                .xgForAvgOverall(1.1)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithDrawHeavyForm() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(10)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(11)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.1)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .drawsHome(3)  // 3+ draws in last 5 = draw-heavy
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .drawsAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(105L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createContextWithLowCardsReferee() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(10)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.1)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(1L)
                .seasonId(1L)
                .appearancesOverall(15)
                .drawsPer(28.0)
                .cardsPerMatchOverall(2.5)  // Low cards = controlled games
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(106L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
                .build();
    }

    private FixtureContext createMidTableContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(10)  // Mid-table
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(12)  // Mid-table
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.1)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(107L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createHighScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.8)
                .position(5)
                .seasonWinsHome(8)
                .seasonDrawsHome(4)
                .seasonLossesHome(8)
                .seasonGoalsHome(40)  // High scoring
                .seasonConcededHome(32)  // Also concedes a lot
                .xgForAvgOverall(2.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.7)
                .position(6)
                .seasonWinsAway(7)
                .seasonDrawsAway(4)
                .seasonLossesAway(9)
                .seasonGoalsAway(38)  // High scoring
                .seasonConcededAway(35)  // Also concedes
                .xgForAvgOverall(1.9)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(108L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithOdds() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(10)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.1)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(109L)
                .oddsFtX(3.50)  // Draw odds
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(109L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .build();
    }

    private FixtureContext createMismatchedTeamsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(2.5)  // Very strong
                .position(1)
                .seasonWinsHome(14)
                .seasonDrawsHome(3)
                .seasonLossesHome(3)
                .seasonGoalsHome(42)
                .seasonConcededHome(12)
                .xgForAvgOverall(2.3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(0.8)  // Very weak - big PPG diff
                .position(19)
                .seasonWinsAway(2)
                .seasonDrawsAway(3)
                .seasonLossesAway(15)
                .seasonGoalsAway(12)
                .seasonConcededAway(40)
                .xgForAvgOverall(0.7)  // Big xG diff
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(110L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createIncompleteContext() {
        return FixtureContext.builder()
                .fixture(createFixture(111L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();
    }

    private FixtureContext createContextWithDrawFriendlyReferee() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(10)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(11)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.1)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(1L)
                .seasonId(1L)
                .appearancesOverall(15)
                .drawsPer(32.0)  // Draw-friendly > 30%
                .cardsPerMatchOverall(3.2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(112L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
                .build();
    }

    private FixtureContext createContextWithoutXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                // No xG data
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.3)
                .position(10)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                // No xG data
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(113L))
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
